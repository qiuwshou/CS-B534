import java.io.*;
import java.util.*;
import java.lang.*;
import mpi.*;

public class MPIPageRank {
    private int MPIrank;
    private int MPIsize;

    // adjacency matrix read from file, inbound list
    private int adjMatrix[][];
    // out-degree
    private int deg[];
    // input file name
    private String inputFile = "";
    // output file name
    private String outputFile = "";
    // number of iterations
    private int iterations = 10;
    // damping factor
    private double df = 0.85;
    // number of URLs
    private int size[] = new int[1];
    // calculating rank values
    private double rankValues[];
    private int chunkSize;

    /**
     * Parse the command line arguments and update the instance variables. Command line arguments are of the form
     * <input_file_name> <output_file_name> <num_iters> <damp_factor>
     *
     * @param args arguments
     */
    public void parseArgs(String[] args) throws IOException {
        if (args.length != 4)
            throw new IOException("wrong number of arguments");

        inputFile  = args[0];
        outputFile = args[1];
        iterations = Integer.parseInt(args[2]);
        df         = Double.parseDouble(args[3]);
    }

    /**
     * Read the input from the file and populate the adjacency matrix
     *
     * The input is of type
     *
     0
     1 2
     2 1
     3 0 1
     4 1 3 5
     5 1 4
     6 1 4
     7 1 4
     8 1 4
     9 4
     10 4
     * The first value in each line is a URL. Each value after the first value is the URLs referred by the first URL.
     * For example the page represented by the 0 URL doesn't refer any other URL. Page
     * represented by 1 refer the URL 2.
     *
     * @throws java.io.IOException if an error occurs
     */
    public void loadInput() throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(inputFile));

	if (MPIrank == 0) {
	    // adjacency matrix read from file, inbound list
	    HashMap<Integer, ArrayList<Integer>> adjMap = new HashMap<Integer, ArrayList<Integer>>();
	    // out-degree
	    HashMap<Integer, Integer> degMap = new HashMap<Integer, Integer>();

	    // processing input file
	    String line;
	    while((line = reader.readLine()) != null) {
		Scanner        scanner = new Scanner(line);
		int                url = scanner.nextInt();

		degMap.put(url, 0);
		if (!adjMap.containsKey(url))
		    adjMap.put(url, new ArrayList<Integer>());
            
		while(scanner.hasNextInt()){
		    int u = scanner.nextInt();
		    if (!adjMap.containsKey(u))
			adjMap.put(u, new ArrayList<Integer>());
		    adjMap.get(u).add(url);
		    degMap.compute(url, (k,v) -> v + 1);
		}
	    }
	    reader.close();
	    size[0] = degMap.size();

	    // Brocast size of graph
	    for (int i = 1; i < MPIsize; i++) {
		MPI.COMM_WORLD.Send(size, 0, 1, MPI.INT, i, 1);
	    }
	    // caculate chunk size
	    chunkSize = size[0] / MPIsize;

	    // put corresponding part into adjMatrix
	    adjMatrix = new int[chunkSize][];
	    for (int i = 0; i < chunkSize; i++)
		adjMatrix[i] = adjMap.get(i).stream().mapToInt(x -> x).toArray();

	    // create outbound degree array
	    deg = new int[size[0]];
	    degMap.forEach((k,v) -> deg[k] = v);

	    // send corresponding adjMatrix to each node
	    int length[] = new int[1];
	    for (int i = chunkSize; i < size[0]; i++) {
		int recv = i / chunkSize;
		if (recv >= MPIsize)
		    recv = MPIsize - 1;
		
		length[0] = adjMap.get(i).size();
		MPI.COMM_WORLD.Send(length, 0, 1, MPI.INT, recv, 1);
		MPI.COMM_WORLD.Send(adjMap.get(i).stream().mapToInt(x -> x).toArray(),
				    0, length[0], MPI.INT, recv, 1);
	    }

	    // send outbound degree array to each node
	    for (int i = 1; i < MPIsize; i++)
		MPI.COMM_WORLD.Send(deg, 0, size[0], MPI.INT, i, 1);
	}
	else {
	    // Receive size of graph
	    MPI.COMM_WORLD.Recv(size, 0, 1, MPI.INT, 0, 1);

	    // Caculate chunk size
	    if (MPIrank == MPIsize - 1)
		chunkSize = size[0] / MPIsize + size[0] % MPIsize;
	    else
		chunkSize = size[0] / MPIsize;

	    // Receive adjMatrix
	    adjMatrix = new int[chunkSize][];
	    int length[] = new int[1];
	    for (int i = 0; i < chunkSize; i++) {
		MPI.COMM_WORLD.Recv(length, 0, 1, MPI.INT, 0, 1);
		adjMatrix[i] = new int[length[0]];
		MPI.COMM_WORLD.Recv(adjMatrix[i], 0, length[0], MPI.INT, 0, 1);
	    }

	    // Receive outbound degree array
	    deg = new int[size[0]];
	    MPI.COMM_WORLD.Recv(deg, 0, size[0], MPI.INT, 0, 1);
	}
    }

    /**
     * Do fixed number of iterations and calculate the page rank values. You may keep the
     * intermediate page rank values in a hash table.
     */
    public void calculatePageRank() {
        double vals[] = new double[chunkSize];
	int offset = (size[0]/MPIsize) * MPIrank;
	rankValues = new double[size[0]];

	// initialization
	if (MPIrank == 0) {
	    for(int i = 0; i < rankValues.length; i++)
		rankValues[i] = 1.0/(double)size[0];
	}
        
        for(int iter = 0; iter < iterations; iter++) {
	    if (MPIrank == 0) {
		// send rankValues to every node
		for (int recv = 1; recv < MPIsize; recv++)
		    MPI.COMM_WORLD.Send(rankValues, 0, size[0], MPI.DOUBLE, recv, 1);
	    }
	    else {
		// receive rankValues
		MPI.COMM_WORLD.Recv(rankValues, 0, size[0], MPI.DOUBLE, 0, 1);
	    }

	    // update rankValues
            for(int i = 0; i < chunkSize; i++) {
	    	double pr = 0.0;
	    	for(int u = 0; u < adjMatrix[i].length; u++)
	    	    pr += rankValues[adjMatrix[i][u]] / (double)deg[adjMatrix[i][u]];

	    	if (deg[offset + i] == 0)
	    	    pr += rankValues[offset + i];

	    	vals[i] = (1.0 - df)/(double)size[0] + df * pr;
	    }

            if (MPIrank == 0) {
		// copy updated results back to rankValues
		for (int i = 0; i < chunkSize; i++)
		    rankValues[i] = vals[i];

		// receive updated results from other nodes
		for(int i = 1; i < MPIsize; i++)
		    MPI.COMM_WORLD.Recv(rankValues, i * chunkSize,
					chunkSize + (i == MPIsize - 1 ? size[0] % MPIsize : 0),
					MPI.DOUBLE, i, 1);
	    }
	    else {
		// send updated results to node 0
		MPI.COMM_WORLD.Send(vals, 0, chunkSize, MPI.DOUBLE, 0, 1);
	    }
        }
    }

    /**
     * Print the pagerank values. Before printing you should sort them according to decreasing order.
     * Print all the values to the output file. Print only the first 10 values to console.
     *
     * @throws IOException if an error occurs
     */
    Comparator<Map.Entry<Integer, Double>> comp = (p1, p2) -> {
        if (p1.getValue() < p2.getValue())
            return 1;
        else if (p1.getValue() > p2.getValue())
            return -1;
        
        return p1.getKey() - p2.getKey();
    };
              
    public void printValues() throws IOException {
        BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile));
        ArrayList<Map.Entry<Integer, Double>> lst = new ArrayList<Map.Entry<Integer, Double>>();

	for (int i=0; i < size[0]; ++i)
	    lst.add(new AbstractMap.SimpleEntry<Integer, Double>(i, rankValues[i]));
        Collections.sort(lst, comp);
        for (int i = 0; i < size[0]; ++i) {
            Map.Entry<Integer, Double> v = lst.get(i);
            if (i < 10)
                System.out.println("Page: " + v.getKey() + " |  Rank: " + v.getValue());
            writer.write("Page: " + v.getKey() + " |  Rank: " + v.getValue() + "\n");
        }
        writer.close();
    }

    public static void main(String[] args) throws IOException {
	String[] parameters = MPI.Init(args);	
        MPIPageRank MPIPR = new MPIPageRank();

	MPIPR.MPIrank = MPI.COMM_WORLD.Rank();
        MPIPR.MPIsize = MPI.COMM_WORLD.Size();

        MPIPR.parseArgs(parameters);
	MPIPR.loadInput();
	MPIPR.calculatePageRank();
	if (MPIPR.MPIrank == 0) {
	    MPIPR.printValues();
	}
	    
	MPI.Finalize();
    }
}
