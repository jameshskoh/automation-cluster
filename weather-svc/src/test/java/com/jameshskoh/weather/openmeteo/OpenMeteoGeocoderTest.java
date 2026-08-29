package com.jameshskoh.weather.openmeteo;

import com.jameshskoh.weather.config.WeatherConfig;
import com.jameshskoh.weather.domain.ResolvedLocation;
import com.jameshskoh.weather.testutil.FixtureHttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenMeteoGeocoderTest {

  private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

  private FixtureHttpServer server;

  @AfterEach
  void tearDown() {
    if (server != null) {
      server.close();
    }
  }

  @Test
  void parsesTheGuidesFiveResultAyerHitamFixture() throws Exception {
    server = FixtureHttpServer.respondingWith(200, readFixture("geocoding-ayer-hitam.json"));
    OpenMeteoGeocoder geocoder = new OpenMeteoGeocoder(HTTP_CLIENT, configFor(server));

    List<ResolvedLocation> results = geocoder.search("Ayer Hitam");

    assertEquals(5, results.size());

    ResolvedLocation johor = results.get(0);
    assertEquals("Ayer Hitam", johor.name());
    assertEquals("Johor", johor.state());
    assertEquals(1732696L, johor.id());
    assertEquals(6745L, johor.population());

    // Second result (Negeri Sembilan) omits "population" in the guide's live sample — must parse
    // as null, not zero.
    ResolvedLocation negeriSembilan = results.get(1);
    assertEquals("Negeri Sembilan", negeriSembilan.state());
    assertNull(negeriSembilan.population());

    // The client returns raw candidates without filtering, so the fuzzy over-match is still present.
    ResolvedLocation overMatch = results.get(4);
    assertEquals("Kampung Ayer Itam", overMatch.name());
    assertEquals("Penang", overMatch.state());

    assertTrue(server.lastRequestUri().contains("name=Ayer+Hitam") || server.lastRequestUri().contains("name=Ayer%20Hitam"));
    assertTrue(server.lastRequestUri().contains("countryCode=MY"));
    assertTrue(server.lastRequestUri().contains("count=10"));
    assertTrue(server.lastRequestUri().contains("language=en"));
  }

  @Test
  void emptyResultsKeyMeansNoMatch() throws Exception {
    server = FixtureHttpServer.respondingWith(200, "{\"generationtime_ms\":0.1}");
    OpenMeteoGeocoder geocoder = new OpenMeteoGeocoder(HTTP_CLIENT, configFor(server));

    List<ResolvedLocation> results = geocoder.search("Nonexistent Town");

    assertTrue(results.isEmpty());
  }

  @Test
  void serverErrorSurfacesAsTransientOpenMeteoException() {
    server = FixtureHttpServer.respondingWith(503, "service unavailable");
    OpenMeteoGeocoder geocoder = new OpenMeteoGeocoder(HTTP_CLIENT, configFor(server));

    assertThrows(
        com.jameshskoh.weather.port.OpenMeteoException.class,
        () -> geocoder.search("Ayer Hitam"));
  }

  private static WeatherConfig configFor(FixtureHttpServer server) {
    return WeatherConfig.of(
        "test-project", "weather-svc-results", server.baseUrl(), server.baseUrl(), 1, 2_000);
  }

  private static String readFixture(String name) throws IOException {
    try (InputStream in = OpenMeteoGeocoderTest.class.getResourceAsStream("/openmeteo/" + name)) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
