package com.xplordat.kbe.beam;

import org.apache.beam.sdk.values.KV;
import org.apache.beam.sdk.transforms.DoFn;

import org.apache.avro.generic.GenericRecord;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import com.xplordat.kbe.avro.RawVertex ;

public class EmitRawVertex extends DoFn<KV<String, GenericRecord>, KV<String,RawVertex>> {
  static Gson gson = new GsonBuilder().setPrettyPrinting().create();
  private static Logger logger = LoggerFactory.getLogger(EmitRawVertex.class);
  @ProcessElement
  public void processElement(@Element KV<String, GenericRecord> kafka_record, OutputReceiver<KV<String, RawVertex>> receiver) {
    try {
      RawVertex vertex = gson.fromJson(kafka_record.getValue().toString(), RawVertex.class) ;
      receiver.output(KV.of(kafka_record.getKey(), vertex)) ;
    }
    catch (Exception err) {
      logger.error("Error", err);
    }
  }
}

