package com.jameshskoh.weather.service;

import com.jameshskoh.weather.domain.ForecastData;
import com.jameshskoh.weather.domain.LocationRequest;
import com.jameshskoh.weather.domain.ResolvedLocation;
import com.jameshskoh.weather.domain.WeatherResult;
import com.jameshskoh.weather.messaging.in.GatewayMessage;
import com.jameshskoh.weather.messaging.in.LocationRequestParser;
import com.jameshskoh.weather.port.BlockAggregator;
import com.jameshskoh.weather.port.Forecaster;
import com.jameshskoh.weather.port.Geocoder;
import com.jameshskoh.weather.port.LocationResolver;
import com.jameshskoh.weather.port.OpenMeteoException;
import com.jameshskoh.weather.port.PublishException;
import com.jameshskoh.weather.port.ResultPublisher;

import java.util.List;
import java.util.Optional;

/**
 * Orchestrates one {@code WEATHER}/{@code REQUESTED} message: resolve -> forecast -> aggregate ->
 * publish, applying the error classification (docs/architecture.md, "Error posture"):
 *
 * <ul>
 *   <li>bad request (undecodable {@code payload}), no geocoding match, or a transient open-meteo
 *       failure (5xx/network/timeout, no retry) -> terminal {@code FAILED} publish, then ack.
 *   <li>publish itself failing -> propagates as {@link PublishException} so the caller returns
 *       non-2xx for Pub/Sub redelivery.
 * </ul>
 */
public final class WeatherService {

  private final Geocoder geocoder;
  private final Forecaster forecaster;
  private final LocationResolver resolver;
  private final BlockAggregator aggregator;
  private final ResultPublisher publisher;

  public WeatherService(
      Geocoder geocoder,
      Forecaster forecaster,
      LocationResolver resolver,
      BlockAggregator aggregator,
      ResultPublisher publisher) {
    this.geocoder = geocoder;
    this.forecaster = forecaster;
    this.resolver = resolver;
    this.aggregator = aggregator;
    this.publisher = publisher;
  }

  public void process(GatewayMessage message) throws PublishException {
    WeatherResult result = resolveResult(message);
    if (result instanceof WeatherResult.Fetched fetched) {
      publisher.publishFetched(message.requestId(), fetched.location(), fetched.blocks());
    } else if (result instanceof WeatherResult.Failed failed) {
      publisher.publishFailed(message.requestId(), failed.reason());
    }
  }

  /** Package-visible so tests can assert the classification without a real publisher. */
  WeatherResult resolveResult(GatewayMessage message) {
    Optional<LocationRequest> parsed = LocationRequestParser.parse(message.payload());
    if (parsed.isEmpty()) {
      return new WeatherResult.Failed(
          "bad request: payload must be a JSON object with non-blank \"city\" and \"state\" fields");
    }
    LocationRequest request = parsed.get();

    List<ResolvedLocation> candidates;
    try {
      candidates = geocoder.search(request.city());
    } catch (OpenMeteoException e) {
      return new WeatherResult.Failed("weather service temporarily unavailable");
    }

    Optional<ResolvedLocation> resolved = resolver.resolve(request.city(), request.state(), candidates);
    if (resolved.isEmpty()) {
      return new WeatherResult.Failed(
          "no match for \"%s\" in \"%s\", Malaysia".formatted(request.city(), request.state()));
    }

    ForecastData forecast;
    try {
      forecast = forecaster.fetch(resolved.get().latitude(), resolved.get().longitude());
    } catch (OpenMeteoException e) {
      return new WeatherResult.Failed("weather service temporarily unavailable");
    }

    return new WeatherResult.Fetched(resolved.get(), aggregator.aggregate(forecast));
  }
}
