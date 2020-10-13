#!/bin/bash

# Author: Andrew Arnold
# $1 is number of processes, $2 is output file name, 

set all_args = $@
set num_proc = $1
set out_name = $2
set i = `expr 0`

for i in {0..`expr $# - 3`}
do
	set src[$i] = ${all_args[`expr $i + 3`]}
done

module load OpenMPI
mpicc  -g  -Wall  -o  $out_name  ${src[@]}
mpiexec  -n  $num_proc  "./$out_name"
