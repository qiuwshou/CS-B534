package edu.iu.km;

import java.io.*;
import java.util.concurrent.ExecutionException;
import java.util.Random;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;

/*
 * generate data and initial centroids
 */
public class Utils { 
    static void deleteDir(File f) {
	if (f.isDirectory()) {
	    for (File file : f.listFiles())
		deleteDir(file);
	}
	f.delete();
    }
    
    static void generateData( int numOfDataPoints, int vectorSize, int numMapTasks, 
			      FileSystem fs,  String localDirStr, Path dataDir)
	throws IOException, InterruptedException, ExecutionException {
	Random random = new Random();
	int numPerFile = numOfDataPoints / numMapTasks;
	File localDir = new File(localDirStr);

	if (localDir.exists()) {
	    deleteDir(localDir);
        }
        localDir.mkdir();

	for (int taski = 0; taski < numMapTasks; ++taski) {
	    DataOutputStream output = new DataOutputStream(new FileOutputStream(localDirStr + File.separator + "data_" + taski));
	    System.out.println("Write " + numPerFile + " nodes to " + localDirStr + File.separator + "data_" + taski);
	    for (int i = 0; i < numPerFile ; ++i) {
		for (int j = 0; j < vectorSize; ++j) {
		    output.writeDouble(random.nextDouble() * 100.0);
		}
	    }

	    output.close();
	}
	
	Path localDirPath = new Path(localDirStr);
        fs.copyFromLocalFile(localDirPath, dataDir);
    }
    
    static void generateInitialCentroids(int numCentroids, int vectorSize, Configuration configuration,
					 Path cDir, FileSystem fs, int JobID) throws IOException {
	Random random = new Random();
	
	if (fs.exists(cDir)) {
            fs.delete(cDir, true);
        }
        if (!fs.mkdirs(cDir)) {
            throw new IOException("Mkdirs failed to create " + cDir.toString());
        }

	Path cPath = new Path(cDir, "init_centroids");
	FSDataOutputStream output = fs.create(cPath, true);
	BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(output));
	System.out.println("Write initial centroids to " + cPath.toString());
	
	for (int i = 0 ; i < numCentroids ; ++i) {
	    for (int j = 0 ; j < vectorSize ; ++j) {
		writer.write((random.nextDouble() * 100.0) + (j < vectorSize - 1 ? " " : ""));
	    }
	    writer.newLine();
	}
	writer.close();
    }
}
