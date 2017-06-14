package edu.iu.km;

import java.io.IOException;
import java.net.URISyntaxException;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;

import edu.iu.fileformat.MultiFileInputFormat;
import edu.iu.km.Utils;

public class KmeansMapCollective  extends Configured implements Tool {

    public static void main(String[] argv) throws Exception {
	int res = ToolRunner.run(new Configuration(), new KmeansMapCollective(), argv);
	System.exit(res);
    }

	
    @Override
    public int run(String[] args) throws Exception {
	//keep this unchanged.
	if (args.length < 6) {
	    System.err.println("Usage: KmeansMapCollective <numOfDataPoints> <num of Centroids> "
			       + "<size of vector> <number of map tasks> <number of iteration> <workDir> <localDir>");			
	    ToolRunner.printGenericCommandUsage(System.err);
	    return -1;
	}

	/* Parse arguments */
	int numDataPoints = Integer.parseInt(args[0]);
        int numCentroids = Integer.parseInt(args[1]);
        int vectorSize = Integer.parseInt(args[2]);
        int numMapTasks = Integer.parseInt(args[3]);
        int numIteration = Integer.parseInt(args[4]);
        String workDir = args[5];
        String localDir = args[6];

	Configuration configuration = this.getConf();
	FileSystem fs = FileSystem.get((Configuration)configuration);
	Path workDirPath = new Path(workDir);
	Path dataDirPath = new Path(workDirPath, "data");
	Path centDirPath = new Path(workDirPath, "centroids");
	Path outDirPath = new Path(workDirPath, "output");
	System.out.println("outDirPath " + outDirPath.toString());

	if (fs.exists(workDirPath)) {
            fs.delete(workDirPath, true);
        }
	fs.mkdirs(workDirPath);

	if (fs.exists(outDirPath)) {
            fs.delete(outDirPath, true);
        }

	// Generate data randomly
	Utils.generateData(numDataPoints, vectorSize, numMapTasks, fs, localDir, dataDirPath);
	// Generate initial centroids
	Utils.generateInitialCentroids(numCentroids, vectorSize, configuration, centDirPath, fs, 0);
	
	// Configure jobs
	Job kmeansjob = configureKMeansJob(numDataPoints, numCentroids, vectorSize,
					   numMapTasks, numIteration, dataDirPath,
					   centDirPath, outDirPath, configuration);

        // Launch job
	boolean jobSuccess = kmeansjob.waitForCompletion(true);
	if (!jobSuccess) {
            System.out.println("KMeans Job fails.");
        }

	return 0;
    }

    private Job configureKMeansJob(int numDataPoints, int numCentroids, int vectorSize,
				   int numMapTasks, int numIteration,
				   Path dataDir, Path centDir, Path outDir,
				   Configuration configuration) throws IOException, URISyntaxException {
        Job job = Job.getInstance(configuration, "kmeans_job");
	JobConf jobConf = (JobConf) job.getConfiguration();
	Configuration jobConfig = job.getConfiguration();
	
        FileInputFormat.setInputPaths(job, dataDir);
        FileOutputFormat.setOutputPath(job, outDir);
        job.setInputFormatClass(MultiFileInputFormat.class);
        job.setJarByClass(KmeansMapCollective.class);
        job.setMapperClass(KmeansMapper.class);
        
        jobConf.set("mapreduce.framework.name", "map-collective");
        jobConf.setNumMapTasks(numMapTasks);
        jobConf.setInt("mapreduce.job.max.split.locations", 10000);
        job.setNumReduceTasks(0);
        
        jobConfig.setInt("points_per_file", numDataPoints / numMapTasks);
        jobConfig.setInt("num_cent", numCentroids);
        jobConfig.setInt("vec_size", vectorSize);
        jobConfig.setInt("num_mapper", numMapTasks);
        jobConfig.setInt("iterations", numIteration);
        jobConfig.set("cenDirStr", centDir.toString());
        return job;
    }
}
