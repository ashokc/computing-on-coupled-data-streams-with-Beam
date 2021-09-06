package com.xplordat.kbe.kafka ;

/* Logging */

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import java.util.Properties ;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import com.xplordat.kbe.avro.* ;

public class Producers {

	Properties producerProps = new Properties() ; 
  private static Logger logger = LoggerFactory.getLogger(Producers.class);

	String topic, startTime ;
	double amplitude, Timeperiod, xReference, yReference ;
  int runTime ;
  long sleepTime ;
	public static void main (String[] args) {
		if (args.length == 13) {
			Producers producers = new Producers() ;
			producers.producerProps.setProperty("client.id", args[0]) ;
			producers.producerProps.setProperty("acks", args[1]) ;
			producers.producerProps.setProperty("retries", args[2]) ;
			producers.producerProps.setProperty("enable.idempotence", args[3]) ;
			producers.producerProps.setProperty("max.in.flight.requests.per.connection", args[4]) ;
			producers.topic = args[5] ;
			producers.amplitude = Double.parseDouble(args[6]) ;
			producers.Timeperiod = Double.parseDouble(args[7]) ;
			producers.xReference = Double.parseDouble(args[8]) ;
			producers.yReference = Double.parseDouble(args[9]) ;
			producers.runTime = Integer.parseInt(args[10]) ;
			producers.startTime = args[11] ;
			producers.sleepTime = Long.parseLong(args[12]) ;

			producers.producerProps.setProperty("bootstrap.servers","localhost:9092") ;
			producers.producerProps.setProperty("key.serializer","org.apache.kafka.common.serialization.StringSerializer") ;
			producers.producerProps.setProperty("value.serializer","io.confluent.kafka.serializers.KafkaAvroSerializer") ;
			producers.producerProps.setProperty("schema.registry.url","http://localhost:8081") ;

			producers.go() ;
		}
		else {
			System.out.println ("USAGE: java -cp ./core/target/kafka.jar com.xplordat.kbe.producers $clientId $acks $retries $enableIdempotence $maxInFlightRequestsPerConnection $topic $amplitude $Timeperiod $xReference $yReference $runTime $startTime $sleepTime") ;
		}
	}

	void go() {
		ProducerProcess producerProcess = new ProducerProcess (topic, producerProps, amplitude, Timeperiod, xReference, yReference, runTime, startTime, sleepTime) ;
		Thread producerThread = new Thread(producerProcess, "thread-"+producerProps.getProperty("client.id")) ;	
		producerThread.start() ;
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				try {
					producerThread.interrupt() ;
					producerThread.join() ;
				}
				catch (Exception e) {
					logger.error ("Errors in shutdownhook..." + e) ;
					System.exit(1) ;
				}
			}
		});
	}

}

