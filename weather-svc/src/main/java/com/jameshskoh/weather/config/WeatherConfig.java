package com.jameshskoh.weather.config;

import java.util.function.Function;

/**
 * Env-driven configuration, validated on first use: a missing required variable throws immediately
 * (POC {@code requireEnv} style), not eagerly at class-load. See docs/architecture.md,
 * "Configuration".
 *
 * <p>{@code FORECAST_DAYS} and {@code HTTP_TIMEOUT_MS} defaults are a phase-3 decision (the design
 * left them unspecified): {@code FORECAST_DAYS} defaults to 1 since v1's report covers a single
 * day. A larger value still works — it just fetches days the outbound payload won't use.
 */
public final class WeatherConfig {

  private static final String DEFAULT_GEOCODING_URL = "https://geocoding-api.open-meteo.com/v1/search";
  private static final String DEFAULT_FORECAST_URL = "https://api.open-meteo.com/v1/forecast";
  private static final int DEFAULT_FORECAST_DAYS = 1;
  private static final int DEFAULT_HTTP_TIMEOUT_MS = 5_000;

  private final String gcpProjectId;
  private final String weatherResultsTopicId;
  private final String openMeteoGeocodingUrl;
  private final String openMeteoForecastUrl;
  private final int forecastDays;
  private final int httpTimeoutMs;

  private WeatherConfig(
      String gcpProjectId,
      String weatherResultsTopicId,
      String openMeteoGeocodingUrl,
      String openMeteoForecastUrl,
      int forecastDays,
      int httpTimeoutMs) {
    this.gcpProjectId = gcpProjectId;
    this.weatherResultsTopicId = weatherResultsTopicId;
    this.openMeteoGeocodingUrl = openMeteoGeocodingUrl;
    this.openMeteoForecastUrl = openMeteoForecastUrl;
    this.forecastDays = forecastDays;
    this.httpTimeoutMs = httpTimeoutMs;
  }

  public static WeatherConfig fromEnv() {
    return fromEnv(System::getenv);
  }

  /** Direct construction (e.g. for tests pointing at a local fixture server). */
  public static WeatherConfig of(
      String gcpProjectId,
      String weatherResultsTopicId,
      String openMeteoGeocodingUrl,
      String openMeteoForecastUrl,
      int forecastDays,
      int httpTimeoutMs) {
    return new WeatherConfig(
        gcpProjectId, weatherResultsTopicId, openMeteoGeocodingUrl, openMeteoForecastUrl,
        forecastDays, httpTimeoutMs);
  }

  /** Package-visible seam so tests can inject a fake environment without touching real env vars. */
  static WeatherConfig fromEnv(Function<String, String> env) {
    return new WeatherConfig(
        requireEnv(env, "GCP_PROJECT_ID"),
        requireEnv(env, "WEATHER_RESULTS_TOPIC_ID"),
        optionalEnv(env, "OPENMETEO_GEOCODING_URL", DEFAULT_GEOCODING_URL),
        optionalEnv(env, "OPENMETEO_FORECAST_URL", DEFAULT_FORECAST_URL),
        optionalIntEnv(env, "FORECAST_DAYS", DEFAULT_FORECAST_DAYS),
        optionalIntEnv(env, "HTTP_TIMEOUT_MS", DEFAULT_HTTP_TIMEOUT_MS));
  }

  public String gcpProjectId() {
    return gcpProjectId;
  }

  public String weatherResultsTopicId() {
    return weatherResultsTopicId;
  }

  public String openMeteoGeocodingUrl() {
    return openMeteoGeocodingUrl;
  }

  public String openMeteoForecastUrl() {
    return openMeteoForecastUrl;
  }

  public int forecastDays() {
    return forecastDays;
  }

  public int httpTimeoutMs() {
    return httpTimeoutMs;
  }

  private static String requireEnv(Function<String, String> env, String name) {
    String value = env.apply(name);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException("Missing required environment variable: " + name);
    }
    return value;
  }

  private static String optionalEnv(Function<String, String> env, String name, String defaultValue) {
    String value = env.apply(name);
    return (value == null || value.isBlank()) ? defaultValue : value;
  }

  private static int optionalIntEnv(Function<String, String> env, String name, int defaultValue) {
    String value = env.apply(name);
    if (value == null || value.isBlank()) {
      return defaultValue;
    }
    try {
      return Integer.parseInt(value.trim());
    } catch (NumberFormatException e) {
      throw new IllegalStateException("Environment variable " + name + " must be an integer, got: " + value, e);
    }
  }
}
