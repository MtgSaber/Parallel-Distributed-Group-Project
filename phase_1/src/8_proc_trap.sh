#!/bin/bash
#PBS -N trap_test_p8
#PBS -l nodes=1:ppn=8
#PBS -l walltime=00:02:00
JOBID=`echo $PBS_JOBID | cut -f1 -d.`
module load OpenMPI
mpiexec -n 8 ./trap_test
exit 0
