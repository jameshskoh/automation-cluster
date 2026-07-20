package com.jameshskoh.weather.service;

import com.jameshskoh.weather.domain.BlockSummary;
import com.jameshskoh.weather.domain.ForecastBlocks;
import com.jameshskoh.weather.domain.ForecastData;
import com.jameshskoh.weather.domain.ResolvedLocation;
import com.jameshskoh.weather.domain.SkyCondition;
import com.jameshskoh.weather.domain.WeatherResult;
import com.jameshskoh.weather.messaging.in.GatewayMessage;
import com.jameshskoh.weather.port.BlockAggregator;
import com.jameshskoh.weather.port.Forecaster;
import com.jameshskoh.weather.port.Geocoder;
import com.jameshskoh.weather.port.LocationResolver;
import com.jameshskoh.weather.port.OpenMeteoException;
import com.jameshskoh.weather.port.PublishException;
import com.jameshskoh.weather.port.ResultPublisher;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeatherServiceTest {

  private static final ResolvedLocation LOCATION = new ResolvedLocation(
      "Ayer Hitam", "Johor", 1.915, 103.1808, "Asia/Kuala_Lumpur", 6745L, 1732696);
  private static final ForecastData FORECAST_DATA = new ForecastData(
      List.of("2026-07-19T00:00"), List.of(26.0), List.of(28.0), List.of(10.0), List.of(0));
  private static final ForecastBlocks BLOCKS = new ForecastBlocks(List.of(new ForecastBlocks.DayBlocks(
      "2026-07-19",
      List.of(new BlockSummary("midnight", 26.0, 24.0, 30.0, 28.0, 10.0, SkyCondition.SUNNY)))));

  @Test
  void badRequestPayloadClassifiesAsFailedWithoutCallingGeocoder() {
    FakeGeocoder geocoder = new FakeGeocoder(List.of(LOCATION));
    WeatherService service = serviceWith(geocoder, new FakeForecaster(FORECAST_DATA),
        new FakeResolver(Optional.of(LOCATION)), new FakeAggregator(BLOCKS), new FakePublisher());

    WeatherResult result = service.resolveResult(requestedMessage("not-json"));

    WeatherResult.Failed failed = assertInstanceOf(WeatherResult.Failed.class, result);
    assertTrue(failed.reason().contains("bad request"));
    assertEquals(0, geocoder.callCount);
  }

  @Test
  void transientGeocodingFailureClassifiesAsFailed() {
    FakeGeocoder geocoder = new FakeGeocoder(new OpenMeteoException("boom"));
    WeatherService service = serviceWith(geocoder, new FakeForecaster(FORECAST_DATA),
        new FakeResolver(Optional.of(LOCATION)), new FakeAggregator(BLOCKS), new FakePublisher());

    WeatherResult result = service.resolveResult(requestedMessage(payload("Ayer Hitam", "Johor")));

    WeatherResult.Failed failed = assertInstanceOf(WeatherResult.Failed.class, result);
    assertEquals("weather service temporarily unavailable", failed.reason());
  }

  @Test
  void noGeocodingMatchClassifiesAsFailed() {
    WeatherService service = serviceWith(new FakeGeocoder(List.of(LOCATION)), new FakeForecaster(FORECAST_DATA),
        new FakeResolver(Optional.empty()), new FakeAggregator(BLOCKS), new FakePublisher());

    WeatherResult result = service.resolveResult(requestedMessage(payload("Ayer Hitam", "Melaka")));

    WeatherResult.Failed failed = assertInstanceOf(WeatherResult.Failed.class, result);
    assertTrue(failed.reason().contains("no match for \"Ayer Hitam\" in \"Melaka\", Malaysia"));
  }

  @Test
  void transientForecastFailureClassifiesAsFailed() {
    WeatherService service = serviceWith(new FakeGeocoder(List.of(LOCATION)),
        new FakeForecaster(new OpenMeteoException("boom")),
        new FakeResolver(Optional.of(LOCATION)), new FakeAggregator(BLOCKS), new FakePublisher());

    WeatherResult result = service.resolveResult(requestedMessage(payload("Ayer Hitam", "Johor")));

    WeatherResult.Failed failed = assertInstanceOf(WeatherResult.Failed.class, result);
    assertEquals("weather service temporarily unavailable", failed.reason());
  }

  @Test
  void happyPathResolvesToFetched() {
    WeatherService service = serviceWith(new FakeGeocoder(List.of(LOCATION)), new FakeForecaster(FORECAST_DATA),
        new FakeResolver(Optional.of(LOCATION)), new FakeAggregator(BLOCKS), new FakePublisher());

    WeatherResult result = service.resolveResult(requestedMessage(payload("Ayer Hitam", "Johor")));

    WeatherResult.Fetched fetched = assertInstanceOf(WeatherResult.Fetched.class, result);
    assertEquals(LOCATION, fetched.location());
    assertEquals(BLOCKS, fetched.blocks());
  }

  @Test
  void processPublishesFetchedOnHappyPath() throws PublishException {
    FakePublisher publisher = new FakePublisher();
    WeatherService service = serviceWith(new FakeGeocoder(List.of(LOCATION)), new FakeForecaster(FORECAST_DATA),
        new FakeResolver(Optional.of(LOCATION)), new FakeAggregator(BLOCKS), publisher);

    service.process(requestedMessage(payload("Ayer Hitam", "Johor")));

    assertEquals(List.of("req-1"), publisher.fetchedRequestIds);
    assertTrue(publisher.failedRequestIds.isEmpty());
  }

  @Test
  void processPublishesFailedOnNoMatch() throws PublishException {
    FakePublisher publisher = new FakePublisher();
    WeatherService service = serviceWith(new FakeGeocoder(List.of(LOCATION)), new FakeForecaster(FORECAST_DATA),
        new FakeResolver(Optional.empty()), new FakeAggregator(BLOCKS), publisher);

    service.process(requestedMessage(payload("Ayer Hitam", "Melaka")));

    assertEquals(List.of("req-1"), publisher.failedRequestIds);
    assertTrue(publisher.fetchedRequestIds.isEmpty());
  }

  private static WeatherService serviceWith(
      Geocoder geocoder, Forecaster forecaster, LocationResolver resolver,
      BlockAggregator aggregator, ResultPublisher publisher) {
    return new WeatherService(geocoder, forecaster, resolver, aggregator, publisher);
  }

  private static GatewayMessage requestedMessage(String payload) {
    return new GatewayMessage("WEATHER", "REQUESTED", "req-1", payload, "");
  }

  private static String payload(String city, String state) {
    return "{\"city\":\"" + city + "\",\"state\":\"" + state + "\"}";
  }

  private static final class FakeGeocoder implements Geocoder {
    private final List<ResolvedLocation> results;
    private final OpenMeteoException failure;
    int callCount = 0;

    FakeGeocoder(List<ResolvedLocation> results) {
      this.results = results;
      this.failure = null;
    }

    FakeGeocoder(OpenMeteoException failure) {
      this.results = null;
      this.failure = failure;
    }

    @Override
    public List<ResolvedLocation> search(String city) throws OpenMeteoException {
      callCount++;
      if (failure != null) throw failure;
      return results;
    }
  }

  private static final class FakeForecaster implements Forecaster {
    private final ForecastData data;
    private final OpenMeteoException failure;

    FakeForecaster(ForecastData data) {
      this.data = data;
      this.failure = null;
    }

    FakeForecaster(OpenMeteoException failure) {
      this.data = null;
      this.failure = failure;
    }

    @Override
    public ForecastData fetch(double latitude, double longitude) throws OpenMeteoException {
      if (failure != null) throw failure;
      return data;
    }
  }

  private static final class FakeResolver implements LocationResolver {
    private final Optional<ResolvedLocation> result;

    FakeResolver(Optional<ResolvedLocation> result) {
      this.result = result;
    }

    @Override
    public Optional<ResolvedLocation> resolve(String city, String state, List<ResolvedLocation> candidates) {
      return result;
    }
  }

  private static final class FakeAggregator implements BlockAggregator {
    private final ForecastBlocks blocks;

    FakeAggregator(ForecastBlocks blocks) {
      this.blocks = blocks;
    }

    @Override
    public ForecastBlocks aggregate(ForecastData data) {
      return blocks;
    }
  }

  private static final class FakePublisher implements ResultPublisher {
    final List<String> fetchedRequestIds = new ArrayList<>();
    final List<String> failedRequestIds = new ArrayList<>();

    @Override
    public void publishFetched(String requestId, ResolvedLocation location, ForecastBlocks blocks) {
      fetchedRequestIds.add(requestId);
    }

    @Override
    public void publishFailed(String requestId, String reason) {
      failedRequestIds.add(requestId);
    }
  }
}
