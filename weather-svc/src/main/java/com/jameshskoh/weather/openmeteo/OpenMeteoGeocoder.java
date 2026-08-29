package com.jameshskoh.weather.openmeteo;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.jameshskoh.weather.config.WeatherConfig;
import com.jameshskoh.weather.domain.ResolvedLocation;
import com.jameshskoh.weather.port.Geocoder;
import com.jameshskoh.weather.port.OpenMeteoException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

/**
 * {@link Geocoder} over the open-meteo Geocoding API: {@code GET /v1/search?name=<city>
 * &countryCode=MY&count=10&language=en} (v1 is Malaysia-only). See
 * docs/arch/open-meteo-integration.md.
 */
public final class OpenMeteoGeocoder implements Geocoder {

  private static final Gson GSON = new Gson();

  private final HttpClient httpClient;
  private final WeatherConfig config;

  public OpenMeteoGeocoder(HttpClient httpClient, WeatherConfig config) {
    this.httpClient = httpClient;
    this.config = config;
  }

  @Override
  public List<ResolvedLocation> search(String city) throws OpenMeteoException {
    URI uri = URI.create(
        config.openMeteoGeocodingUrl()
            + "?name=" + urlEncode(city)
            + "&countryCode=MY&count=10&language=en");

    HttpRequest request = HttpRequest.newBuilder(uri)
        .timeout(Duration.ofMillis(config.httpTimeoutMs()))
        .GET()
        .build();

    HttpResponse<String> response = send(request, "geocoding");

    if (response.statusCode() != 200) {
      throw new OpenMeteoException(
          "open-meteo geocoding API returned status " + response.statusCode());
    }

    GeocodingResponseDto dto;
    try {
      dto = GSON.fromJson(response.body(), GeocodingResponseDto.class);
    } catch (JsonSyntaxException e) {
      throw new OpenMeteoException("Failed to parse open-meteo geocoding response", e);
    }

    // A genuine no-match returns HTTP 200 with the "results" key absent entirely, not an empty
    // array (confirmed in the guide) — an empty list here, not an exception.
    if (dto == null || dto.results() == null) {
      return List.of();
    }
    return dto.results().stream().map(GeocodingResultDto::toResolvedLocation).toList();
  }

  private HttpResponse<String> send(HttpRequest request, String apiName) throws OpenMeteoException {
    try {
      return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    } catch (HttpTimeoutException e) {
      throw new OpenMeteoException("Timeout calling open-meteo " + apiName + " API", e);
    } catch (IOException e) {
      throw new OpenMeteoException("Network error calling open-meteo " + apiName + " API", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new OpenMeteoException("Interrupted calling open-meteo " + apiName + " API", e);
    }
  }

  private static String urlEncode(String value) {
    return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  /**
   * The Geocoding API's response shape. {@code results} is absent entirely (not an empty array) on
   * a genuine no-match — {@link #results()} may be {@code null}.
   */
  private record GeocodingResponseDto(List<GeocodingResultDto> results) {}

  /**
   * One entry of the {@code results} array; field names match the JSON (see
   * docs/arch/open-meteo-integration.md). An absent {@code population} key stays {@code null}.
   */
  private record GeocodingResultDto(
      long id,
      String name,
      double latitude,
      double longitude,
      String admin1,
      Long population,
      String timezone) {

    ResolvedLocation toResolvedLocation() {
      return new ResolvedLocation(name, admin1, latitude, longitude, timezone, population, id);
    }
  }
}
