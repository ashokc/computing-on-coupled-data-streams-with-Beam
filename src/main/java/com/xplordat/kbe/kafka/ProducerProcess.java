package com.xplordat.kbe.kafka;

/* Logging */

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import java.util.Arrays ;
import java.util.stream.Collectors ;
import java.util.stream.IntStream ;
import java.util.Collections ;
import java.util.Properties;
import java.util.concurrent.ThreadLocalRandom;
import java.io.InputStream;
import java.time.ZonedDateTime;
import java.time.LocalDate ;
import java.time.LocalTime ;
import java.util.TimeZone ;
import java.time.ZoneId;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;

import java.util.List ;
import java.util.ArrayList ;

import org.json.JSONObject;
import org.json.JSONArray;

import com.xplordat.kbe.avro.RawVertex ;

public class ProducerProcess implements Runnable {

	//	Y = A * sin (w*t).	w: angular velocity. radans/second . w = 2*PI/T. T => time period in seconds

	private static Logger logger = LoggerFactory.getLogger(ProducerProcess.class);

	private List<Integer> arrival_order = new ArrayList<Integer>() ;

	String topic, clientId, startTime;
	Properties producerProps;
	private double amplitude, Timeperiod, xReference, yReference;
	private ThreadLocalRandom random = ThreadLocalRandom.current() ;
	private Producer<String, RawVertex> producer;
	private int runTime;  // seconds
	private long sleepTime;  // milliseconds

	double valX= 0.0, valY = 0.0 ;
	private int partitionNumber = 0;

	ProducerProcess(String topic, Properties producerProps, double amplitude, double Timeperiod, double xReference, double yReference, int runTime, String startTime, long sleepTime) {
		this.topic = topic;
		this.producerProps = producerProps;
		this.amplitude = amplitude;
		this.Timeperiod = Timeperiod;
		this.xReference = xReference;
		this.yReference = yReference;
		this.runTime = runTime;
		this.startTime = startTime;
		this.sleepTime = sleepTime;
		producer = new KafkaProducer<String, RawVertex> (producerProps);
	}

  void getXY (double z) {
    double dx = 0.0, dy = 0.0 ;
		if (clientId.equals("A")) {
			dx = -z * Math.cos(Math.PI / 6.0); // 30 degrees
			dy = -z * Math.sin(Math.PI / 6.0); // 30 degrees
		}
    else if (clientId.equals("B")) {
			dx = z * Math.cos(Math.PI / 6.0); // 30 degrees
			dy = -z * Math.sin(Math.PI / 6.0); // 30 degrees
		}
    else if (clientId.equals("C")) {
			dx = 0.0;
			dy = z;
		}
		valX = xReference + dx;
		valY = yReference + dy;
  }

	@Override
	public void run() {
    try {
	    double omega = 2.0 * Math.PI / Timeperiod ;
		  clientId = producerProps.getProperty("client.id");

      ZoneId zoneId = ZoneId.of(TimeZone.getDefault().getID()) ;
      ZonedDateTime zdt = LocalDate.now(zoneId).atTime(LocalTime.MIDNIGHT).atZone(zoneId) ;

      String[] time_pieces = startTime.split(":") ;
      time_pieces[0] = time_pieces[0].replace("\\^0", "") ;
      int hrs = Integer.parseInt(time_pieces[0]) ;
      int minutes = Integer.parseInt(time_pieces[1]) ;
      long timeStart = (zdt.toEpochSecond() + 3600*hrs + 60*minutes)*1000; // milliseconds to startTime
      System.out.println (timeStart) ;

      int count = 0 ;
      int n_obs_per_second = 1000 / ((int) sleepTime) ;
      int totalCount = runTime * n_obs_per_second + 1 ; // produce 10 observations in a second
      long production_times[] = new long[totalCount] ;
      arrival_order = IntStream.range(0, totalCount).boxed().collect(Collectors.toList()) ;
      Collections.shuffle(arrival_order) ;  // kafka arrival order

      List<ProducerRecord<String, RawVertex>> records = new ArrayList<ProducerRecord<String, RawVertex>>() ;
      RawVertex[] vertices = new RawVertex[totalCount] ;

      for (int production_index = 0 ; production_index < totalCount ; production_index++) {
        production_times[production_index] = timeStart + production_index * sleepTime ; // An observation every 'sleepTime' milliseconds
        double z = amplitude * Math.cos(omega*(production_times[production_index] - timeStart)*1.0e-3) ;
        getXY(z) ;
        int arrival_index = arrival_order.get(production_index) ;
        vertices[production_index] = new RawVertex(clientId, production_times[production_index], valX, valY, (long) production_index, (long) arrival_index) ;
        records.add(new ProducerRecord<>(topic, partitionNumber, production_times[production_index], clientId, vertices[production_index])) ;
      }

      Thread.sleep(runTime + 1000); // arrival_time > production_time for all vertices. max delay is run_time for any vertex
      for (int arrival_index: arrival_order) {
			  RecordMetadata metadata = producer.send(records.get(arrival_index)).get();
//			  logger.info("RV & Producer Record Timestamps" + vertices[arrival_index].getProductionTime() + " => " + metadata.timestamp()) ;
        Thread.sleep(sleepTime);
      }
		}
		catch (InterruptedException e) {
			logger.error("Producer " + producerProps.getProperty("client.id") + " Was Interrupted", e);
		}
		catch (Exception e) {
			logger.error("Producer " + producerProps.getProperty("client.id") + " ran into some errors", e);
		}
		finally {
			producer.close();
		}
	}

}
