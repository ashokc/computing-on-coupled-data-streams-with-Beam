package com.xplordat.kbe.beam;

import org.apache.beam.sdk.transforms.windowing.IntervalWindow ;

import org.apache.beam.sdk.values.KV;
import org.apache.beam.sdk.transforms.DoFn;
import com.google.gson.Gson;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import java.util.List ;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import org.apache.commons.math3.analysis.interpolation.LinearInterpolator ;
import org.apache.commons.math3.analysis.interpolation.UnivariateInterpolator;
import org.apache.commons.math3.analysis.UnivariateFunction;

import com.xplordat.kbe.avro.* ;

public class EmitEstimatedVertex extends DoFn<KV<String, Iterable<RawVertex>>, KV<Long, EstimatedVertex>> {
  private static Logger logger = LoggerFactory.getLogger(EmitEstimatedVertex.class);

  private static Gson gson = new Gson();

  String vertex_estimation_strategy ;

  public EmitEstimatedVertex (String vertex_estimation_strategy) {
    this.vertex_estimation_strategy = vertex_estimation_strategy ;
  }

  @ProcessElement
  public void processElement(ProcessContext c, IntervalWindow window, OutputReceiver<KV<Long, EstimatedVertex>> receiver) throws Exception {
    try {
      List<RawVertex> vertices = Lists.newArrayList(c.element().getValue());
      String sensor = c.element().getKey() ;

      long window_start = window.start().getMillis() ;
      long window_end = window.end().getMillis() ;
		  double window_mid_time = (window_start + window_end)/2.0 ;
      long window_id = (long) window_mid_time ;
      String s_window_id = window_id + "";

      SortedMap<Long, RawVertex> byTime = new TreeMap<Long, RawVertex>() ;
      long obs_max_time_val = window_start - 1000, obs_min_time_val = window_end + 1000 ;
      for (RawVertex rv: vertices) {
        long time_val = rv.getProductionTime() ;
        if (time_val < window_start) {
          logger.error("Observation < Window Start:" + time_val + " => " + window_start) ;
          logger.error("rv:" + rv) ; 
          System.exit(1) ;
        }
        if (time_val > window_end) {
          logger.error("Observation > Window End:" + time_val + " => " + window_end) ;
          logger.error("rv:" + rv) ; 
          System.exit(1) ;
        }
        obs_min_time_val = Math.min(time_val, obs_min_time_val) ;
        obs_max_time_val = Math.max(time_val, obs_max_time_val) ;
        byTime.put(time_val, rv) ;
      }

      if ( (window_mid_time <= obs_max_time_val) && (window_mid_time >= obs_min_time_val) ) {

        double x = 0.0, y = 0.0 ;
        EstimatedVertex ev = null ;
        if (vertex_estimation_strategy.equals("average")) {
          int i = 0 ;
          for (Map.Entry<Long,RawVertex> entry : byTime.entrySet()) {
            i++ ;
            x = x + entry.getValue().getX() ;
            y = y + entry.getValue().getY() ;
          }
          x = x / i ;
          y = y / i ;
          ev = new EstimatedVertex (sensor, x, y, window_id, i, System.currentTimeMillis()) ;
        }
        else if (vertex_estimation_strategy.equals("interpolate")) {
          int n_unique_measurements = byTime.size() ;

          double[] obs_time_values = new double[n_unique_measurements] ;
          double[] x_values = new double[n_unique_measurements] ;
          double[] y_values = new double[n_unique_measurements] ;
          int i = -1 ;
          for (Map.Entry<Long,RawVertex> entry : byTime.entrySet()) {
            i++ ;
            obs_time_values[i] = (double) entry.getKey() ;
            x_values[i] = entry.getValue().getX() ;
            y_values[i] = entry.getValue().getY() ;
          }
          UnivariateInterpolator interpolator = new LinearInterpolator();
		      UnivariateFunction function_x = interpolator.interpolate(obs_time_values, x_values);
		      UnivariateFunction function_y = interpolator.interpolate(obs_time_values, y_values);

          x = function_x.value(window_mid_time) ;
          y = function_y.value(window_mid_time) ;
          ev = new EstimatedVertex (sensor, x, y, window_id, obs_time_values.length, System.currentTimeMillis()) ;
        }
        receiver.output(KV.of(window_id, ev)) ;
      }
    }
    catch (Exception err) {
      logger.error("Error", err);
    }
  }
}

