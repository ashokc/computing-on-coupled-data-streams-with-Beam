#!/bin/bash

when="$1"

for sensor in A B C; do
	echo "echo \"./produce.sh $sensor $when \" | at $when"
	echo "./produce.sh $sensor $when " | at $when
done
sleep 60
./beam.sh

