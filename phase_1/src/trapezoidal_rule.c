#include <mpi.h>

double serial_trap_eval(double (*f)(double), double a, double b, int num_trap) {
	double h = (b-a)/num_trap;
	double sum = 0;
	int i;
	
	for (i = 1; i < num_trap; i++)
	{
		sum += f(a + i * h);
	}
	
	return h/2 * (f(a) + f(b) + 2*sum);
}

// param fx is function to apply trapezoidal rule to
// param a, b is range of x values to integrate over
// param num_trap is number of trapezoids to use
// returns full estimate if my_rank==0, 0 otherwise.
double parallel_trap_eval(double (*f)(double), double a, double b, int num_trap) {
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
	local_integral = serial_trap_eval(f, local_a_coeff * h, local_b_coeff * h, local_b_coeff - local_a_coeff);
	// send sum to proc 0
	MPI_Reduce(&local_integral, &full_integral, 1, MPI_DOUBLE, MPI_SUM, 0, MPI_COMM_WORLD);
	return (my_rank==0? full_integral : 0);
}
