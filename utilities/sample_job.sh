#!/bin/bash
#PBS -N aarnol64_test1
#PBS -l nodes=1:ppn=1
#PBS -l walltime=00:10:00
JOBID=`echo $PBS_JOBID | cut -f1 -d.`

echo hello

exit 0
