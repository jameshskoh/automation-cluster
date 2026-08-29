package com.jameshskoh.weather.openmeteo;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.annotations.SerializedName;
import com.jameshskoh.weather.config.WeatherConfig;
import com.jameshskoh.weather.domain.ForecastData;
import com.jameshskoh.weather.port.Forecaster;
import com.jameshskoh.weather.port.OpenMeteoException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;

/**
 * {@link Forecaster} over the open-meteo Forecast API: {@code GET /v1/forecast?latitude=<lat>
 * &longitude=<lon>&hourly=temperature_2m,apparent_temperature,precipitation_probability,
 * weather_code&timezone=Asia/Kuala_Lumpur&forecast_days=<FORECAST_DAYS>}. See
 * docs/arch/open-meteo-integration.md.
 */
public final class OpenMeteoForecaster implements Forecaster {

  private static final Gson GSON = new Gson();
  private static final String HOURLY_VARS =
      "temperature_2m,apparent_temperature,precipitation_probability,weather_code";
  private static final String TIMEZONE = "Asia/Kuala_Lumpur";

  private final HttpClient httpClient;
  private final WeatherConfig config;

  public OpenMeteoForecaster(HttpClient httpClient, WeatherConfig config) {
    this.httpClient = httpClient;
    this.config = config;
  }

  @Override
  public ForecastData fetch(double latitude, double longitude) throws OpenMeteoException {
    URI uri = URI.create(
        config.openMeteoForecastUrl()
            + "?latitude=" + latitude
            + "&longitude=" + longitude
            + "&hourly=" + HOURLY_VARS
            + "&timezone=" + java.net.URLEncoder.encode(TIMEZONE, java.nio.charset.StandardCharsets.UTF_8)
            + "&forecast_days=" + config.forecastDays());

    HttpRequest request = HttpRequest.newBuilder(uri)
        .timeout(Duration.ofMillis(config.httpTimeoutMs()))
        .GET()
        .build();

    HttpResponse<String> response = send(request);

    if (response.statusCode() != 200) {
      throw new OpenMeteoException(
          "open-meteo forecast API returned status " + response.statusCode());
    }

    ForecastResponseDto dto;
    try {
      dto = GSON.fromJson(response.body(), ForecastResponseDto.class);
    } catch (JsonSyntaxException e) {
      throw new OpenMeteoException("Failed to parse open-meteo forecast response", e);
    }

    if (dto == null || dto.hourly() == null) {
      throw new OpenMeteoException("open-meteo forecast response is missing hourly data");
    }

    HourlyDto hourly = dto.hourly();
    return new ForecastData(
        hourly.time(),
        hourly.temperature2m(),
        hourly.apparentTemperature(),
        hourly.precipitationProbability(),
        hourly.weatherCode());
  }

  private HttpResponse<String> send(HttpRequest request) throws OpenMeteoException {
    try {
      return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    } catch (HttpTimeoutException e) {
      throw new OpenMeteoException("Timeout calling open-meteo forecast API", e);
    } catch (IOException e) {
      throw new OpenMeteoException("Network error calling open-meteo forecast API", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new OpenMeteoException("Interrupted calling open-meteo forecast API", e);
    }
  }

  /** The Forecast API's top-level response shape, restricted to what v1 reads. */
  private record ForecastResponseDto(HourlyDto hourly) {}

  /**
   * The {@code hourly} object, restricted to the four variables v1 needs. Keys are snake_case,
   * mapped via {@link SerializedName}. See docs/arch/open-meteo-integration.md.
   */
  private record HourlyDto(
      List<String> time,
      @SerializedName("temperature_2m") List<Double> temperature2m,
      @SerializedName("apparent_temperature") List<Double> apparentTemperature,
      @SerializedName("precipitation_probability") List<Double> precipitationProbability,
      @SerializedName("weather_code") List<Integer> weatherCode) {}
}
