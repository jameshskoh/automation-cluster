package com.jameshskoh.weather.service;

import com.jameshskoh.weather.config.WeatherConfig;
import com.jameshskoh.weather.forecast.BlockAggregatorImpl;
import com.jameshskoh.weather.messaging.out.PubSubResultPublisher;
import com.jameshskoh.weather.openmeteo.OpenMeteoForecaster;
import com.jameshskoh.weather.openmeteo.OpenMeteoGeocoder;
import com.jameshskoh.weather.port.BlockAggregator;
import com.jameshskoh.weather.port.Forecaster;
import com.jameshskoh.weather.port.Geocoder;
import com.jameshskoh.weather.port.LocationResolver;
import com.jameshskoh.weather.port.ResultPublisher;
import com.jameshskoh.weather.resolve.LocationResolverImpl;

import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Wires the ports to their implementations, reading config from the environment. Built once per
 * warm container instance, not per request.
 */
public final class CompositionRoot {

  private CompositionRoot() {}

  public static WeatherService buildWeatherService() {
    WeatherConfig config = WeatherConfig.fromEnv();

    HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofMillis(config.httpTimeoutMs()))
        .build();

    Geocoder geocoder = new OpenMeteoGeocoder(httpClient, config);
    Forecaster forecaster = new OpenMeteoForecaster(httpClient, config);
    LocationResolver resolver = new LocationResolverImpl();
    BlockAggregator aggregator = new BlockAggregatorImpl();

    ResultPublisher publisher;
    try {
      publisher = PubSubResultPublisher.forTopic(config);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to create Pub/Sub publisher for topic "
          + config.weatherResultsTopicId(), e);
    }

    return new WeatherService(geocoder, forecaster, resolver, aggregator, publisher);
  }
}
