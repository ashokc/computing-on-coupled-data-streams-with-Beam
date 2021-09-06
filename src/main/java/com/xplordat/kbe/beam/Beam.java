package com.xplordat.kbe.beam;

import java.util.Properties;
import java.io.InputStream;
import java.sql.PreparedStatement;

import org.apache.beam.sdk.options.PipelineOptions;
import org.apache.beam.sdk.options.PipelineOptionsFactory;
import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.PipelineResult;

import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.sdk.transforms.GroupByKey;
import org.apache.beam.sdk.transforms.windowing.FixedWindows;
import org.apache.beam.sdk.transforms.windowing.Window;
import org.apache.beam.sdk.transforms.windowing.Repeatedly ;
import org.apache.beam.sdk.transforms.windowing.AfterPane ;

import org.apache.beam.sdk.values.KV;
import org.apache.beam.sdk.values.PCollection;
import org.apache.beam.sdk.io.kafka.KafkaIO;
import org.apache.beam.sdk.io.kafka.ConfluentSchemaRegistryDeserializerProvider ;
import org.apache.beam.sdk.io.elasticsearch.ElasticsearchIO ;

import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.avro.generic.GenericRecord;

import com.google.common.collect.ImmutableMap;
import org.joda.time.Duration;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import com.xplordat.kbe.avro.* ;

public class Beam {
  private static Logger logger = LoggerFactory.getLogger(Beam.class);
  private static Properties props ;
  static {
    try {
      InputStream propsStream = Thread.currentThread().getContextClassLoader().getResourceAsStream("application.properties");
      props = new Properties();
      props.load(propsStream);
      propsStream.close();
    }
    catch (Exception e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  void run(PipelineOptions options) throws Exception {
    Pipeline pipeline = Pipeline.create(options) ;
    String beam_consume_kafka_topic_start = props.getProperty("beam_consume_kafka_topic_start") ;
    Boolean beam_consume_kafka_topic_commit = Boolean.TRUE ;
    if (beam_consume_kafka_topic_start.equals("earliest")) {
    	beam_consume_kafka_topic_commit = Boolean.FALSE ;
    }
    else if (beam_consume_kafka_topic_start.equals("latest")) {
    	beam_consume_kafka_topic_commit = Boolean.TRUE ;
    }
    int beam_window_size = Integer.parseInt(props.getProperty("beam_window_size")) ;

    PCollection<KV<String, RawVertex>> vertices = pipeline
      .apply(KafkaIO.<String, GenericRecord>read()
        .withBootstrapServers(props.getProperty("kafka_broker"))
        .withTopic(props.getProperty("kafka_topic"))
        .withKeyDeserializer(StringDeserializer.class)
        .withValueDeserializer(ConfluentSchemaRegistryDeserializerProvider.of(props.getProperty("schema_registry"), props.getProperty("kafka_topic") + "-value"))
        .updateConsumerProperties(ImmutableMap.of("auto.offset.reset", (Object) beam_consume_kafka_topic_start, "enable.auto.commit", (Object) beam_consume_kafka_topic_commit))
        .withCreateTime(Duration.ZERO) // KafkaTimestampType.CREATE_TIME +/- 0 seconds
        .withoutMetadata()) // PCollection<KV<String, GenericRecord>>
      .apply(ParDo.of(new EmitRawVertex())); // PCollection<KV<String, RawVertex>>

// Window the vertices

    long runTime = Long.parseLong(props.getProperty("runTime")) ;

    PCollection<KV<String, RawVertex>> windowedVertices = vertices
      .apply(Window.<KV<String, RawVertex>>into(FixedWindows.of(Duration.standardSeconds(beam_window_size)))
        .withAllowedLateness(Duration.standardSeconds(2*runTime), Window.ClosingBehavior.FIRE_ALWAYS)
        .triggering(Repeatedly.forever(AfterPane.elementCountAtLeast(15)))
        .accumulatingFiredPanes()) ; // PCollection<KV<String, RawVertex>>

    String vertex_estimation_strategy = props.getProperty("vertex_estimation_strategy") ;
    String[] esHosts = new String[] {"https://" + props.getProperty("es_host") + ":" + Integer.parseInt(props.getProperty("es_port"))} ;

    windowedVertices
      .apply(GroupByKey.<String, RawVertex>create()) // PCollection<KV<String, <Iterable<RawVertex>>>
      .apply(ParDo.of(new EmitEstimatedVertex(vertex_estimation_strategy))) // PCollection<KV<Long, EstimatedVertex>>
      .apply(GroupByKey.<Long, EstimatedVertex>create()) // PCollection<KV<Long, <Iterable<EstimatedVertex>>> 
      .apply(ParDo.of(new EmitTriangle())) // PCollection<Triangle>>
      .apply(ParDo.of(new EmitJsonTriangle())) // PCollection<String>
      .apply(ElasticsearchIO.write()
        .withConnectionConfiguration(ElasticsearchIO.ConnectionConfiguration
           .create(esHosts, props.getProperty("es_triangle_index"))
           .withApiKey(props.getProperty("es_api_key"))
           .withKeystorePath("local-es.jks")
           .withKeystorePassword("changeme"))) ;

    PipelineResult result = pipeline.run();

    try {
      result.waitUntilFinish();
    }
    catch (Exception exc) {
      result.cancel();
    }
  }

  public static void main(String[] args) throws Exception {
    PipelineOptions options = PipelineOptionsFactory.fromArgs(args).create();
    Beam beam = new Beam() ;
    beam.run(options) ;
  }
}
