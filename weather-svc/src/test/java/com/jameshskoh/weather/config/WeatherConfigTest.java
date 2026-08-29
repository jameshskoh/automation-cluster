package com.jameshskoh.weather.config;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WeatherConfigTest {

  @Test
  void throwsOnMissingRequiredVar() {
    Map<String, String> env = Map.of("WEATHER_RESULTS_TOPIC_ID", "weather-svc-results");

    IllegalStateException e =
        assertThrows(IllegalStateException.class, () -> WeatherConfig.fromEnv(env::get));

    assertEquals("Missing required environment variable: GCP_PROJECT_ID", e.getMessage());
  }

  @Test
  void appliesDefaultsWhenOptionalVarsAbsent() {
    Map<String, String> env = Map.of(
        "GCP_PROJECT_ID", "my-project",
        "WEATHER_RESULTS_TOPIC_ID", "weather-svc-results");

    WeatherConfig config = WeatherConfig.fromEnv(env::get);

    assertEquals("my-project", config.gcpProjectId());
    assertEquals("weather-svc-results", config.weatherResultsTopicId());
    assertEquals("https://geocoding-api.open-meteo.com/v1/search", config.openMeteoGeocodingUrl());
    assertEquals("https://api.open-meteo.com/v1/forecast", config.openMeteoForecastUrl());
    assertEquals(1, config.forecastDays());
    assertEquals(5_000, config.httpTimeoutMs());
  }

  @Test
  void overridesDefaultsWhenOptionalVarsPresent() {
    Map<String, String> env = Map.of(
        "GCP_PROJECT_ID", "my-project",
        "WEATHER_RESULTS_TOPIC_ID", "weather-svc-results",
        "OPENMETEO_GEOCODING_URL", "http://localhost:1/search",
        "OPENMETEO_FORECAST_URL", "http://localhost:1/forecast",
        "FORECAST_DAYS", "3",
        "HTTP_TIMEOUT_MS", "1234");

    WeatherConfig config = WeatherConfig.fromEnv(env::get);

    assertEquals("http://localhost:1/search", config.openMeteoGeocodingUrl());
    assertEquals("http://localhost:1/forecast", config.openMeteoForecastUrl());
    assertEquals(3, config.forecastDays());
    assertEquals(1234, config.httpTimeoutMs());
  }
}
