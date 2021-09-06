#!/bin/bash

function monitor() { 
	clientId=$1
	acks=$2
	retries=$3
	enableIdempotence=$4
	maxInFlightRequestsPerConnection=$5
	topic=$6
	amplitude=$7
	time_period=${8}	# seconds
	xReference=${9}
	yReference=${10}
	runTime=${11}		# in seconds
	startTime=${12}	# HH:MM
	sleepTime=${13}	# HH:MM

	echo "java -cp ../target/kafka-beam-es-bundled-1.0-SNAPSHOT.jar com.xplordat.kbe.kafka.Producers $clientId $acks $retries $enableIdempotence $maxInFlightRequestsPerConnection $topic $amplitude $time_period $xReference $yReference $runTime $startTime $sleepTime"

	java -cp ../target/kafka-beam-es-bundled-1.0-SNAPSHOT.jar com.xplordat.kbe.kafka.Producers $clientId $acks $retries $enableIdempotence $maxInFlightRequestsPerConnection $topic $amplitude $time_period $xReference $yReference $runTime $startTime $sleepTime

}

#monitor monitor1 all 0 false 1 true feature1,feature2 1000

#monitorName=$1
#amplitude=$2
#xReference=$3
#yReference=$4

#	Args: clientId acks retries enableIdempotence maxInFlightRequestsPerConnection topic amplitude angularV xReference yReference

sensor=$1
start_time=$2

if [ "$sensor" == "A" ]; then
	monitor A all 0 false 1 raw-vertex 0.5 90 0.0 0.0 360 $start_time 10
elif [ "$sensor" == "B" ]; then
	monitor B all 0 false 1 raw-vertex 0.5 15 1.0 0.0 360 $start_time 10
elif [ "$sensor" == "C" ]; then
	monitor C all 0 false 1 raw-vertex 0.5 45 0.5 0.8660254037844386 360 $start_time 10
fi


