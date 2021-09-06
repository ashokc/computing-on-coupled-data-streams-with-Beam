#!/bin/bash

java -cp ../target/kafka-beam-es-bundled-1.0-SNAPSHOT.jar com.xplordat.kbe.beam.Beam --runner=Directrunner --targetParallelism=1

