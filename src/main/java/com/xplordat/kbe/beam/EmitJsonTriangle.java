package com.xplordat.kbe.beam;

import org.apache.beam.sdk.transforms.DoFn;

import com.google.gson.Gson;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import com.xplordat.kbe.avro.Triangle ;

public class EmitJsonTriangle extends DoFn<Triangle, String> {
  static Gson gson = new Gson();
  private static Logger logger = LoggerFactory.getLogger(EmitJsonTriangle.class);
  @ProcessElement
	public void processElement(@Element Triangle triangle, OutputReceiver<String> receiver) {
		try {
      receiver.output(gson.toJson(triangle)) ;
    }
    catch (Exception err) {
    	logger.error("Error", err);
    }
  }
}

