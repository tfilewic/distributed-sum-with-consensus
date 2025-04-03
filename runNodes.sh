# script to run multiple gradle node commands
# @author tfilewic

#!/bin/bash
#usage: ./runNodes.sh <count> <faults>
if [ $# -ne 2 ]; then
  echo "usage: $0 <count> <faults>"
  exit 1
fi

count=$1
faults=$2

#check if negative
if [ $count -lt 0 ]; then
  	echo "ERROR: count ($count) cannot be negative."
  	exit 1
fi

if [ $faults -lt 0 ]; then
  	echo "ERROR: faults ($faults) cannot be negative."
  	exit 1
fi

#fault ceiling is count
if [ $faults -gt $count ]; then
  	echo "faults ($faults) greater than count ($count). setting faults to count."
  	faults=$count
fi

nonFaults=$((count - faults))
declare -a nodes

#add "true" for faulty nodes
for (( i=1; i<=faults; i++ )); do
  	nodes+=("true")
done

#add "false" for non-faulty nodes
for (( i=1; i<=nonFaults; i++ )); do
  	nodes+=("false")
done

#shuffle the array
shuffled=($(printf "%s\n" "${nodes[@]}" | sort -R))

#launch nodes...
for i in "${!shuffled[@]}"; do
  	flag="${shuffled[$i]}"
  	echo "	running \"gradle node Pfault=${flag}\" ..."
	gradle node -Pfault=${flag} -q --console=plain &
done

echo " "
wait

