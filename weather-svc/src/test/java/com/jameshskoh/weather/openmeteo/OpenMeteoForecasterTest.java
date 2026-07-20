package com.jameshskoh.weather.openmeteo;

import com.jameshskoh.weather.config.WeatherConfig;
import com.jameshskoh.weather.domain.ForecastData;
import com.jameshskoh.weather.port.OpenMeteoException;
import com.jameshskoh.weather.testutil.FixtureHttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenMeteoForecasterTest {

  private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

  private FixtureHttpServer server;

  @AfterEach
  void tearDown() {
    if (server != null) {
      server.close();
    }
  }

  @Test
  void parsesTheGuidesSampleForecastAndIssuesTheDocumentedQuery() throws Exception {
    server = FixtureHttpServer.respondingWith(200, readFixture("forecast-sample.json"));
    WeatherConfig config = WeatherConfig.of(
        "test-project", "weather-svc-results", server.baseUrl(), server.baseUrl(), 2, 2_000);
    OpenMeteoForecaster forecaster = new OpenMeteoForecaster(HTTP_CLIENT, config);

    ForecastData data = forecaster.fetch(3.1073, 101.6067);

    assertEquals(48, data.time().size());
    assertEquals("2026-07-13T00:00", data.time().get(0));
    assertEquals(26.6, data.temperature2m().get(0));
    assertEquals(31.7, data.apparentTemperature().get(0));
    assertEquals(0.0, data.precipitationProbability().get(0));
    assertEquals(3, data.weatherCode().get(0));

    String uri = server.lastRequestUri();
    assertTrue(uri.contains("latitude=3.1073"));
    assertTrue(uri.contains("longitude=101.6067"));
    assertTrue(uri.contains("hourly=temperature_2m%2Capparent_temperature%2Cprecipitation_probability%2Cweather_code")
        || uri.contains("hourly=temperature_2m,apparent_temperature,precipitation_probability,weather_code"));
    assertTrue(uri.contains("timezone=Asia%2FKuala_Lumpur"));
    assertTrue(uri.contains("forecast_days=2"));
  }

  @Test
  void networkFailureSurfacesAsTransientOpenMeteoException() {
    // Nothing listening on this port: connection refused -> IOException -> OpenMeteoException.
    WeatherConfig config = WeatherConfig.of(
        "test-project", "weather-svc-results", "http://localhost:1", "http://localhost:1", 1, 500);
    OpenMeteoForecaster forecaster = new OpenMeteoForecaster(HTTP_CLIENT, config);

    assertThrows(OpenMeteoException.class, () -> forecaster.fetch(3.1073, 101.6067));
  }

  private static String readFixture(String name) throws IOException {
    try (InputStream in = OpenMeteoForecasterTest.class.getResourceAsStream("/openmeteo/" + name)) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
