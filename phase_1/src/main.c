#include <string.h>
#include <stdio.h>
#include <mpi.h>
#include "trapezoidal_rule.c"
#include "trap_test_functions.c"

int main(void) {
	int my_rank;
	size_t num_tests = 1;
	size_t tests_per_func = 5000;
	
	// testing input data
	double (*func_list[])(double) = {
		quadratic_a
	};
	char * func_names[] = {
		"quadratic a"
	};
	double a_inputs[num_tests];
	double b_inputs[num_tests];
	int trap_count_inputs[num_tests];
	
	// testing output data
	double parallel_integrals[num_tests];
	double parallel_exec_times[num_tests];
	double serial_integrals[num_tests];
	double serial_exec_times[num_tests];
	
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
			scanf("%lf %lf %d", &a, &b, &n);
		}
		
		// share input results to other processes
		MPI_Bcast(&a, 1, MPI_DOUBLE, 0, MPI_COMM_WORLD);
		MPI_Bcast(&b, 1, MPI_DOUBLE, 0, MPI_COMM_WORLD);
		MPI_Bcast(&n, 1, MPI_INT, 0, MPI_COMM_WORLD);
		
		// store into arrays
		a_inputs[test_count] = a;
		b_inputs[test_count] = b;
		trap_count_inputs[test_count] = n;
	}
	
	// conduct timed tests for parallel implmentation
	double start_time, end_time, local_elapsed_time, elapsed_time, integral;
	size_t i;
	for (test_count = 0; test_count < num_tests; test_count++) {
		
		for (i = 0; i < tests_per_func; i++) {
			// do timer setup
			// start timer
			MPI_Barrier(MPI_COMM_WORLD);
			start_time = MPI_Wtime();
			
			// do trapezoidal rule
			integral = parallel_trap_eval(
					func_list[test_count],
					a_inputs[test_count],
					b_inputs[test_count],
					trap_count_inputs[test_count]
			);
			
			// end timer
			end_time = MPI_Wtime();
			local_elapsed_time = end_time - start_time;
			MPI_Reduce(&local_elapsed_time, &elapsed_time, 1, MPI_DOUBLE, MPI_MAX, 0, MPI_COMM_WORLD);
		
			// record time and result
			if (my_rank == 0) {
				parallel_integrals[test_count] = integral;
				parallel_exec_times[test_count] += elapsed_time;
			}
		}
		if (my_rank == 0) {
			parallel_exec_times[test_count] /= tests_per_func;
		}
	}
	
	// TODO: display output
	if (my_rank == 0) {
		for (test_count = 0; test_count < num_tests; test_count++) {
			printf(
				"Estimate / Mean Execution Time for function \"%s\", tested %d times with a=%lf, b=%lf, sub-integrals=%d: %lf / %lf\n",
				func_names[test_count],
				tests_per_func,
				a_inputs[test_count],
				b_inputs[test_count],
				trap_count_inputs[test_count],
				parallel_integrals[test_count],
				parallel_exec_times[test_count]
			);
		}
	}
	
	MPI_Finalize();
	return 0;
}