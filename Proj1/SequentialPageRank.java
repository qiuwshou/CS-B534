import java.io.*;
import java.util.*;
import java.lang.*;

public class SequentialPageRank {
    // adjacency matrix read from file
    private HashMap<Integer, ArrayList<Integer>> adjMatrix = new HashMap<Integer, ArrayList<Integer>>();
    // out-degree
    private HashMap<Integer, Integer> deg = new HashMap<Integer, Integer>();
    // input file name
    private String inputFile = "";
    // output file name
    private String outputFile = "";
    // number of iterations
    private int iterations = 10;
    // damping factor
    private double df = 0.85;
    // number of URLs
    private int size = 0;
    // calculating rank values
    private HashMap<Integer, Double> rankValues = new HashMap<Integer, Double>();

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
        String line;
        while((line = reader.readLine()) != null) {
            Scanner        scanner = new Scanner(line);
            int                url = scanner.nextInt();

            deg.put(url, 0);
            if (!adjMatrix.containsKey(url))
                adjMatrix.put(url, new ArrayList<Integer>());
            
            while(scanner.hasNextInt()){
                int u = scanner.nextInt();
                if (!adjMatrix.containsKey(u))
                    adjMatrix.put(u, new ArrayList<Integer>());
                adjMatrix.get(u).add(url);
                deg.compute(url, (k,v) -> v + 1);
            }
        }
        size = deg.size();
        reader.close();
    }

    /**
     * Do fixed number of iterations and calculate the page rank values. You may keep the
     * intermediate page rank values in a hash table.
     */
    public void calculatePageRank() {
        HashMap<Integer, Double> vals = new HashMap<Integer, Double>();
        adjMatrix.forEach((url, lst) -> rankValues.put(url, 1.0/(double)size));
        
        for(int i = 0; i < iterations; ++i) {
            adjMatrix.forEach((url, lst) -> {
                    double pr = 0.0;
		    for(int u : lst)
			pr += rankValues.get(u) / (double)deg.get(u);
                    
                    if (deg.get(url) == 0)
                        pr += rankValues.get(url);
                    
                    vals.put(url, (1.0 - df)/(double)size + df * pr);
                });
            rankValues.putAll(vals);
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
        rankValues.entrySet().forEach(v -> lst.add(v));
        Collections.sort(lst, comp);
        for (int i = 0; i < size; ++i) {
            Map.Entry<Integer, Double> v = lst.get(i);
            if (i < 10)
                System.out.println("Page: " + v.getKey() + " |  Rank: " + v.getValue());
            writer.write("Page: " + v.getKey() + " |  Rank: " + v.getValue() + "\n");
        }
        writer.close();
    }

    public static void main(String[] args) throws IOException {
        SequentialPageRank sequentialPR = new SequentialPageRank();

        sequentialPR.parseArgs(args);
        sequentialPR.loadInput();
        sequentialPR.calculatePageRank();
        sequentialPR.printValues();
    }
}
