#include <string.h>
#include <stdio.h>
#include <mpi.h>
#include "trapezoidal_rule.c"

int main(void) {
	int my_rank;
	double integral;
	// declare all input variables here as well
	
	MPI_Init(NULL, NULL);
	MPI_Comm_rank(MPI_COMM_WORLD, &my_rank);
	
	if (my_rank == 0) {
		// do your I/O here
	}
	// broadcast your I/O results one variable at a time using:
	// MPI_Bcast(&YOUR_INPUT_VAR_HERE, 1, MPI_DATATYPE_FOR_VARIABLE_HERE, 0, MPI_COMM_WORLD);
	
	// do timer setup
	// start timer
	integral = parallel_trap_eval(YOUR_FUNCTION_NAME_HERE, a, b, num_trap);
	// end timer
	
	// report integral result and time taken
	
	// you can repeat this process as many times as needed,
	// with any f(x) you want, so long as you define it above main
	// and pass the function name to parallel_trap_eval().
	
	MPI_Finalize();
	return 0;
}