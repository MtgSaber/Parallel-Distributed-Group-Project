#include <stdio.h>
#include <string.h>
#include <mpi.h>

double serial_trap_eval(double (*fx)(double), double a, double b, int num_trap) {
	// todo: serial trapezoidal rule implementation
	return 1; // todo: replace with proper result
}

// param fx is function to apply trapezoidal rule to
// param a, b is range of x values to integrate over
// param num_trap is number of trapezoids to use
// returns full estimate if my_rank==0, 0 otherwise.
double parallel_trap_eval(double (*fx)(double), double a, double b, int num_trap) {
	int comm_sz, my_rank;
	const double h = (b-a)/num_trap;
	double local_integral, full_integral;
	
	// MPI stuff -- init is done in main!
	MPI_Comm_size(MPI_COMM_WORLD, &comm_sz);
	MPI_Comm_rank(MPI_COMM_WORLD, &my_rank);
	
	// decide on subset indices by distributing remainder among processes
	const int trap_div = num_trap / comm_sz;
	const int trap_rem = num_trap % comm_sz;
	const int local_a_coeff = (
			my_rank <= trap_rem?
			(trap_div+1) * my_rank
			: (trap_div+1) * trap_rem + trap_div * (my_rank - trap_rem)
	);
	const int local_b_coeff = local_a_coeff + trap_div + (my_rank < trap_rem? 1 : 0);
	
	// run serial trapezoidal rule for local range
	local_integral = serial_trap_eval(fx, local_a_coeff * h, local_b_coeff * h, local_b_coeff - local_a_coeff);
	// send sum to proc 0
	MPI_Reduce(&local_integral, &full_integral, 1, MPI_DOUBLE, MPI_SUM, 0, MPI_COMM_WORLD);
	return (my_rank==0? full_integral, 0);
}

// this will be relocated to separate driver file in final version
double quadratic_1(double x) {
	return x*x + 2*x + 1;
}

// this will be relocated to separate driver file in final version
int main(void) {
	int my_rank
	int* num_trap;
	double* a;
	double* b;

	MPI_Init(NULL, NULL);
	MPI_Comm_rank(MPI_COMM_WORLD, &my_rank);

	if (my_rank==0) {
		printf("Enter a, b, & number of trapezoids:\n");
		scanf("%lf %lf %d", a, b, num_trap);
	}
	MPI_Bcast(a, 1, MPI_DOUBLE, 0, MPI_COMM_WORLD);
	MPI_Bcast(b, 1, MPI_DOUBLE, 0, MPI_COMM_WORLD);
	MPI_Bcast(num_trap, 1, MPI_INT, 0, MPI_COMM_WORLD);
	
	const double integral = parallel_trap_eval(quadratic_1, a, b, num_trap);
	if (my_rank==0) {
		printf("Integral approximation: %lf", integral);
	}
	MPI_Finalize();
	return 0;
}
