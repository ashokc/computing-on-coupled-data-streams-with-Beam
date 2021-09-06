package com.xplordat.kbe.beam;

import java.util.List;

import org.apache.beam.sdk.values.KV;
import org.apache.beam.sdk.transforms.DoFn;

import java.io.InputStream;
import java.util.Properties;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import org.apache.beam.sdk.transforms.windowing.IntervalWindow ;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.json.JSONObject;

import com.xplordat.kbe.avro.* ;

public class EmitTriangle extends DoFn<KV<Long, Iterable<EstimatedVertex>>, Triangle> {

  private static Logger logger = LoggerFactory.getLogger(EmitTriangle.class);

  private double x_a=0, x_b=0, x_c=0, y_a=0, y_b=0, y_c=0 ;
  private int total_samples_used = 0;
  private List<EstimatedVertex> estimatedVertices ;

  boolean findXY (String vertex) {
    int i = -1, n_samples = 0, selected_i = -1 ;
    for (EstimatedVertex ev: estimatedVertices) {
      i++ ;
      if (ev.getSensor().equals(vertex)) {
        if (ev.getNsamples() > n_samples) {
          n_samples = ev.getNsamples() ;
          selected_i = i ;
        }
      }
    }
    if (selected_i > -1) {
      EstimatedVertex ev = estimatedVertices.get(selected_i) ;
      total_samples_used = total_samples_used + ev.getNsamples() ;
      if (ev.getSensor().equals("A")) {
        x_a = ev.getX() ;
        y_a = ev.getY() ;
      }
      else if (ev.getSensor().equals("B")) {
        x_b = ev.getX() ;
        y_b = ev.getY() ;
      }
      else if (ev.getSensor().equals("C")) {
        x_c = ev.getX() ;
        y_c = ev.getY() ;
      }
//      logger.info("\t" + ev.getSensor() + " => total_samples_used:" + total_samples_used) ;
      return true ;
    }
    else {
      return false;
    }
  }

  double findLength (double x1, double y1, double x2, double y2) {
    return Math.sqrt ( (y2-y1)*(y2-y1) + (x2-x1)*(x2-x1) ) ;
  }

  @ProcessElement
  public void processElement(ProcessContext c, IntervalWindow window, OutputReceiver<Triangle> receiver) throws Exception {
    try {
      estimatedVertices = Lists.newArrayList(c.element().getValue());

      long window_start = window.start().getMillis() ;
      long window_end = window.end().getMillis() ;
      long window_mid_time = (long) (window_start + window_end)/2 ;
      long time_value = c.element().getKey() ;
      if (time_value != window_mid_time) {
        logger.error("Window Mid Point Time is NOT equal the Key of the EstimatedVertices... Exiting!!!") ;
        System.out.printf("Triangle window start = %s, end = %s, mid = %s, time_value = %s%n", window_start, window_end, window_mid_time, time_value) ;
        System.exit(1) ;
      }
      int n_vertices = estimatedVertices.size() ;
//      System.out.printf("Triangle window start = %s, end = %s, mid = %s, #vertices = %s%n", window_start, window_end, window_mid_time, n_vertices) ;

      if (n_vertices >= 3) {
        total_samples_used = 0;
        x_a=0; x_b=0; x_c=0; y_a=0; y_b=0; y_c=0 ;
        long compute_time = System.currentTimeMillis() ;
        double ab=0.0, bc=0.0, ca=0.0 ;
        if ( findXY("A") && findXY("B") && findXY("C") ) {
          ab = findLength(x_a, y_a, x_b, y_b) ;
          bc = findLength(x_b, y_b, x_c, y_c) ;
          ca = findLength(x_c, y_c, x_a, y_a) ;
      	  double perimeter = ab + bc + ca ;
      	  double halfPerimeter = perimeter * 0.5 ;
      	  double area = Math.sqrt (halfPerimeter*(halfPerimeter-ab)*(halfPerimeter-bc)*(halfPerimeter-ca)) ;
//      logger.info (ab + " " + bc + " " + ca + " " + perimeter + " " + halfPerimeter + " " + area) ;
          Triangle triangle = new Triangle(window_start, window_end, window_mid_time, ab, bc, ca, perimeter, area, total_samples_used, compute_time) ;
          receiver.output(triangle) ;
        }
      }
    }
    catch (Exception err) {
      logger.error("Error", err);
    }
  }
}

