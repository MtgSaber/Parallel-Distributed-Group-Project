#include <string.h>
#include <stdio.h>
#include <mpi.h>
#include "trapezoidal_rule.c"
#include "trap_test_functions.c"

int main(void) {
	int my_rank;
	size_t num_tests = 1;
	
	// testing input data
	double (*func_list[num_tests])(double) = {
		quadratic_a
	};
	char * func_names[num_tests] = {
		"quadratic a"
	};
	double a_inputs[num_tests];
	double b_inputs[num_tests];
	int trap_count_inputs[num_tests];
	
	// testing output data
	double integrals[num_tests];
	double exec_times[num_tests];
	
	// start MPI
	MPI_Init(NULL, NULL);
	MPI_Comm_rank(MPI_COMM_WORLD, &my_rank);
	
	// get input data
	size_t test_count;
	for (test_count = 0; test_count < num_tests; test_count++) {
		double a, b;
		int n;
		
		// only do stdio on proc 0
		if (my_rank == 0) {
			printf(
					"Enter left boundary, right boundary, and # of sub-intervals for function \"%s\":\n",
					func_names[test_count]
			);
			scanf("%lf %lf $d", &a, &b, &n);
		}
		
		// share input results to other processes
		MPI_Bcast(&a, 1, MPI_DOUBLE, 0, MPI_COMM_WORLD);
		MPI_Bcast(&b, 1, MPI_DOUBLE, 0, MPI_COMM_WORLD);
		MPI_Bcast(&h, 1, MPI_INT, 0, MPI_COMM_WORLD);
		
		// store into arrays
		a_inputs[test_count] = a;
		b_inputs[test_count] = b;
		trap_count_inputs[test_count] = n;
	}
	
	// conduct timed tests
	double start_time, end_time, local_elapsed_time, elapsed_time;
	for (test_count = 0; test_count < num_tests; test_count++) {
		// do timer setup
		// start timer
		MPI_Barrier(MPI_COMM_WORLD);
		MPI_Wtime(start_time);
		
		// do trapezoidal rule
		integral = parallel_trap_eval(
				func_list[test_count],
				a_inputs[test_count],
				b_inputs[test_count],
				trap_count_inputs[test_count]
		);
		
		// end timer
		MPI_Wtime(end_time);
		local_elapsed_time = end_time - start_time;
		MPI_Reduce(&local_elapsed_time, &elapsed_time, 1, MPI_DOUBLE, MPI_MAX, 0, MPI_COMM_WORLD);
		
		// record time and result
		if (my_rank == 0) {
			integrals[test_count] = integral;
			exec_times[test_count] = elapsed_time;
		}
	}
	
	// TODO: display output
	
	MPI_Finalize();
	return 0;
}