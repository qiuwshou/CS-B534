package edu.iu.km;

import java.util.*;
import java.io.*;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.mapred.CollectiveMapper;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;

import edu.iu.harp.example.DoubleArrPlus;
import edu.iu.harp.partition.Partition;
import edu.iu.harp.partition.Table;
import edu.iu.harp.resource.DoubleArray;
import edu.iu.harp.partition.Partitioner;

public class KmeansMapper extends CollectiveMapper<String, String, Object, Object> {
    private int pointsPerFile;
    private int numCentroids;
    private int vectorSize;
    private int numMappers;
    private int numIterations;
    private String cenDirStr;
    private Configuration conf;
    
    @Override
    protected void setup(Context context) throws IOException, InterruptedException {
	conf = context.getConfiguration();

	pointsPerFile = conf.getInt("points_per_file",100);
	numCentroids  = conf.getInt("num_cent", 10);
	vectorSize    = conf.getInt("vec_size", 10);
        numMappers    = conf.getInt("num_mapper", 2);
        numIterations = conf.getInt("iterations", 10);
        cenDirStr     = conf.get("cenDirStr");
    }

    protected void mapCollective( KeyValReader reader, Context context) throws IOException, InterruptedException {
	ArrayList<double[][]> points = new ArrayList<double[][]>();
	Table<DoubleArray>    cTable = new Table(0, new DoubleArrPlus());
	Table<DoubleArray>    nTable = new Table(0, new DoubleArrPlus());
	Random                random = new Random();

	// read points from data files
	while (reader.nextKeyValue()) {
	    String key = reader.getCurrentKey();
	    String value = reader.getCurrentValue();
	    System.out.println("Worker " + this.getSelfID() + ": Key: " + key + ", Value: " + value);
	    points.add(loadFile(value));
	}

	initTable(cTable, nTable);

	// load initial centroids
	if (isMaster()) {
	    loadCentroids(cenDirStr + File.separator + "init_centroids", cTable);
	}

	// brocast centroids
	broadcast("kmean", "broadcast-centroids", cTable, getMasterID(), false);

	for (int iter = 0 ; iter < numIterations ; ++iter) {
	    ArrayList<int[]> mins = computeMin(points, cTable, nTable);
      	    updateCentroids(points, mins, cTable, nTable);

	    regroup("kmean", "regroup_cTable" + iter, cTable, new Partitioner(getNumWorkers()));

	    allgather("kmean", "allgather_nTable" + iter, nTable);

	    double[] nary = ((DoubleArray)(nTable.getPartition(0)).get()).get();
	    for (Partition par : cTable.getPartitions()) {
		double[] ary = ((DoubleArray)par.get()).get();

		if (nary[par.id()] == 0.0) {
		    // zero cluster, randomly pick a point to be new centroids
		    double[][] p = points.get(0);
		    int        r = random.nextInt(p.length);

		    for (int i = 0 ; i < vectorSize ; ++i) {
			ary[i] = p[r][i];
		    }
		}
		else {
		    // update centroids
		    for (int i = 0 ; i < vectorSize ; ++i) {
			ary[i] /= nary[par.id()];
		    }
		}
	    }

	    allgather("kmean", "allgather_cTable" + iter, cTable);
	}

	if (isMaster()) {
	    storeCentroids(cenDirStr + File.separator + "centroids", cTable);
	}
	
	cTable.release();
	nTable.release();
    }

    public void initTable(Table<DoubleArray> cTable, Table<DoubleArray> nTable) {
	for ( int i = 0 ; i < numCentroids ; ++i) {
	    DoubleArray ary = DoubleArray.create(vectorSize, false);
	    cTable.addPartition(new Partition(i, ary));
	}
	nTable.addPartition(new Partition(0, DoubleArray.create(numCentroids, false)));
    }

    public void storeCentroids(String filename, Table<DoubleArray> cTable) throws IOException {
	Path path = new Path(filename);
	FileSystem fs = path.getFileSystem(conf);
	FSDataOutputStream output = fs.create(path, true);
	BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(output));
	System.out.println("Write centroids to " + path.toString());

	for (Partition par : cTable.getPartitions()) {
	    double[] ary = ((DoubleArray)par.get()).get();
	    for (int i = 0 ; i < vectorSize ; ++i) {
		writer.write(ary[i] + (i < vectorSize - 1 ? " " : ""));
	    }
	    writer.newLine();
	}
	writer.close();
    }
    
    public void loadCentroids(String filename, Table<DoubleArray> cTable) throws IOException {
	Path path = new Path(filename);
        FileSystem fs = path.getFileSystem(conf);
        FSDataInputStream in = fs.open(path);
	BufferedReader reader = new BufferedReader(new InputStreamReader(in));

	try {
	    for (Partition par : cTable.getPartitions()) {
		double[] ary = ((DoubleArray)par.get()).get();
		Scanner scanner = new Scanner(reader.readLine());
		
		for (int j = 0 ; j < vectorSize ; ++j) {
		    ary[j] = scanner.nextDouble();
		}
	    }
	}
	finally {
	    in.close();
	}
    }

    public double[][] loadFile(String filename) throws IOException {
	double points[][] = new double[pointsPerFile][];
	Path path = new Path(filename);
        FileSystem fs = path.getFileSystem(conf);
        FSDataInputStream in = fs.open(path);
	
	try {
	    for(int i = 0 ; i < pointsPerFile ; ++i) {
		points[i] = new double[vectorSize];
		for (int j = 0 ; j < vectorSize ; ++j) {
		    points[i][j] = in.readDouble();
		}
	    }
	}
	finally {
	    in.close();
	}
	return points;
    }

    public void printTable(Table<DoubleArray> table) {
	for (Partition par : table.getPartitions()) {
	    double[] ary = ((DoubleArray)par.get()).get();
	    System.out.print(par.id() + " : ");

	    for (int j = 0 ; j < ary.length ; ++j) {
		System.out.print(ary[j] + " ");
	    }
	    System.out.println();
	}
    }

    public double dist2(double[] x, double[] y) {
	double dist, diff;

	dist = 0.0;
	for (int i = 0 ; i < vectorSize ; ++i) {
	    diff = x[i] - y[i];
	    dist += diff * diff;
	}
	
	return dist;
    }
    
    public ArrayList<int[]> computeMin(List<double[][]> pointsList, Table<DoubleArray> cTable,
				       Table<DoubleArray> nTable) {
	double[] nary = ((DoubleArray)(nTable.getPartition(0)).get()).get();
	double[][] centroids = new double[numCentroids][];
	ArrayList<int[]> minList = new ArrayList<int[]>();

	Arrays.fill(nary, 0.0);
	for (Partition par : cTable.getPartitions()) {
	    centroids[par.id()] = ((DoubleArray)par.get()).get();
	}

	for (double[][] points : pointsList) {
	    int[]  mins = new int[points.length];
	    double curDist;
	    int    min;
	    
	    for (int i = 0 ; i < points.length ; ++i) {
		curDist = dist2(points[i], centroids[0]);
		min = 0;
		
	    	for (int j = 1 ; j < numCentroids ; ++j) {
	    	    if (curDist > dist2(points[i], centroids[j]))
	    		min = j;
	    	}

	    	nary[min]+=1.0;
	    	mins[i] = min;
	    }

	    minList.add(mins);
	}

	return minList;
    }

    public void updateCentroids(List<double[][]> pointsList, List<int[]> minList,
				Table<DoubleArray> cTable, Table<DoubleArray> nTable) {
	double[] nary = ((DoubleArray)(nTable.getPartition(0)).get()).get();
	double[][] centroids = new double[numCentroids][];

	for (Partition par : cTable.getPartitions()) {
	    centroids[par.id()] = ((DoubleArray)par.get()).get();
	    Arrays.fill(centroids[par.id()], 0.0);
	}

	for (int lst = 0 ; lst < pointsList.size() ; ++lst) {
	    double[][] points = pointsList.get(lst);
	    int[]      mins   = minList.get(lst);

	    for (int i = 0 ; i < points.length ; ++i) {
		for (int j = 0 ; j < vectorSize ; ++j)
		    centroids[mins[i]][j] += points[i][j];
	    }
	}
    }
}
