#include <math.h>

double quadratic_a(double x) {
	return x * x;
}

double quadratic_b(double x) {
	return x * x * 5 - 20 * x + 50;
}

double exponential(double x) {
	return pow(2, x);
}

double logarithmic(double x) {
	return log(x);
}

double inverse(double x) {
	return 1 / x;
}

// TODO: Add a variety of functions, with differing complexities. Give them reasonable names as well.
