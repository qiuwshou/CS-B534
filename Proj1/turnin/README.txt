Java 1.8 or above is required. 
To complie use the following command:
javac SequentialPageRank.java

To execute it requires four arguments
java SequentialPageRank [input] [output] [iterations] [df]
[input]      input filename
[output]     output filename
[iterations] number of iterations
[df]         damping factor
Example:
java SequentialPageRank pagerank.input pagerank.output 100 0.85

Only top 10 rank will be output to stdout, the complete rank will be output to the output file.
