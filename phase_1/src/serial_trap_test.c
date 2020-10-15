#include <stdio.h>
#include <math.h>

//define f(x) to be integrated
double f(double x){
	return 1/(1+pow(x,2));
}

int main(void){
	int n, i;
	double a,b,h,x,sum=0, integral;

	//input sub-intervals for integration
	printf("Enter the number of sub-intervals: ");
	scanf("%d", &n);

	//input lower bounds of integral
	printf("Enter the lower bounds of the intergral: ");
	scanf("%lf", &a);

	//input upper bounds of integral
	printf("Enter the upper bounds of the integral: ");
	scanf("%lf", &b);

	h = (b-a)/n;

	for (i = 1; i <= n-1; i++)
	{
		x = a + i * h;
		sum = sum + f(x);
	}

	integral = (h/2) * (f(a)+f(b)+2*sum);

	printf("\nThe integral is: %lf\n", integral);

	return 0;
}