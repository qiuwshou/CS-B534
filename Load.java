package edu.iu.mlr;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;


public class Load{
	public  HashMap<String,ArrayList<String>> LoadTopic(String cFileName, Configuration configuration) throws IOException{
		HashMap<String,ArrayList<String>> topics = new HashMap<String, ArrayList<String>>();
		Path cPath = new Path(cFileName);
		FileSystem fs = FileSystem.get(configuration);
		FSDataInputStream in = fs.open(cPath);
		BufferedReader br = new BufferedReader(new InputStreamReader(in));
		String line = "";
		String[] parts = null;
		
		while((line = br.readLine()) != null){
			parts = line.split("\\s+");
			String did = parts[1];
			if(topics.containsKey(did)){
				ArrayList<String> record = topics.get(did);
				record.add(parts[0]);
				topics.put(did, record);
			}
			else{
				ArrayList<String> new_record = new ArrayList<String>();
				new_record.add(parts[0]);
				topics.put(did,new_record);
			}
		}
		return topics;
	}
	
	
	public HashMap<String, HashMap<String,Double>> LoadData(String cFileName, Configuration configuration) throws IOException{
		HashMap<String, HashMap<String,Double>> train = new HashMap<String, HashMap<String,Double>>();
		Path cPath = new Path(cFileName);
		FileSystem fs = FileSystem.get(configuration);
		FSDataInputStream in = fs.open(cPath);
		BufferedReader br = new BufferedReader(new InputStreamReader(in));
		String line = "";
		String[] parts = null;
		
		while((line = br.readLine()) != null){
			parts = line.split("\\s+");
			String did = parts[0];
			HashMap<String,Double> pair = new HashMap<String, Double>();
			for(int i = 1; i < parts.length; i++){
				String[] sub_parts = parts[i].split(":");
				pair.put(sub_parts[0], Double.parseDouble(sub_parts[1]));
			}
			train.put(did, pair);
		}
		return train;
	}
}