Java 1.8 and mpj 0.38 is required. 
To complie use the following command:
javac -cp .:$MPJ_HOME/lib/mpj.jar MPIPageRank.java

The program need to be run by mpjrun.sh, it takes four arguments
mpjrun.sh -np 10 MPIPageRank [input] [output] [iterations] [df]
[input]      input filename
[output]     output filename
[iterations] number of iterations
[df]         damping factor
Example:
mpjrun.sh -np 10 MPIPageRank pagerank.input.1000.urls.6 pagerank.output.1000.urls.6 100 0.85

Only top 10 rank will be output to stdout, the complete rank will be output to the output file.
