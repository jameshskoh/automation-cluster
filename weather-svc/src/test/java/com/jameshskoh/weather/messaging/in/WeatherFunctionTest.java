package com.jameshskoh.weather.messaging.in;

import com.jameshskoh.weather.domain.BlockSummary;
import com.jameshskoh.weather.domain.ForecastBlocks;
import com.jameshskoh.weather.domain.ForecastData;
import com.jameshskoh.weather.domain.ResolvedLocation;
import com.jameshskoh.weather.domain.SkyCondition;
import com.jameshskoh.weather.port.OpenMeteoException;
import com.jameshskoh.weather.port.PublishException;
import com.jameshskoh.weather.port.ResultPublisher;
import com.jameshskoh.weather.service.WeatherService;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end through the real push-envelope parsing + {@link WeatherService} orchestration, wired
 * to fake ports so no network/Pub/Sub call happens.
 */
class WeatherFunctionTest {

  private static final ResolvedLocation LOCATION = new ResolvedLocation(
      "Ayer Hitam", "Johor", 1.915, 103.1808, "Asia/Kuala_Lumpur", 6745L, 1732696);
  private static final ForecastBlocks BLOCKS = new ForecastBlocks(List.of(new ForecastBlocks.DayBlocks(
      "2026-07-19",
      List.of(new BlockSummary("midnight", 26.0, 24.0, 30.0, 28.0, 10.0, SkyCondition.SUNNY)))));

  @Test
  void happyPathParsesEnvelopePublishesFetchedAndAcks() throws Exception {
    RecordingPublisher publisher = new RecordingPublisher();
    WeatherService service = new WeatherService(
        city -> List.of(LOCATION),
        (lat, lon) -> new ForecastData(List.of(), List.of(), List.of(), List.of(), List.of()),
        (city, state, candidates) -> Optional.of(LOCATION),
        data -> BLOCKS,
        publisher);
    WeatherFunction function = new WeatherFunction(service);

    FakeHttpRequest request = new FakeHttpRequest(pushEnvelope("req-1", payload("Ayer Hitam", "Johor")));
    FakeHttpResponse response = new FakeHttpResponse();

    function.service(request, response);

    assertEquals(200, response.statusCode());
    assertEquals("req-1", publisher.fetchedRequestId);
    assertNull(publisher.failedRequestId);
  }

  @Test
  void noGeocodingMatchPublishesFailedAndStillAcks() throws Exception {
    RecordingPublisher publisher = new RecordingPublisher();
    WeatherService service = new WeatherService(
        city -> List.of(),
        (lat, lon) -> new ForecastData(List.of(), List.of(), List.of(), List.of(), List.of()),
        (city, state, candidates) -> Optional.empty(),
        data -> BLOCKS,
        publisher);
    WeatherFunction function = new WeatherFunction(service);

    FakeHttpRequest request = new FakeHttpRequest(pushEnvelope("req-2", payload("Nowhere", "Melaka")));
    FakeHttpResponse response = new FakeHttpResponse();

    function.service(request, response);

    assertEquals(200, response.statusCode());
    assertEquals("req-2", publisher.failedRequestId);
    assertTrue(publisher.failedReason.contains("no match"));
  }

  @Test
  void undecodablePushEnvelopeReturns500ForRedelivery() throws Exception {
    WeatherService service = new WeatherService(
        city -> List.of(), (lat, lon) -> {
          throw new OpenMeteoException("unreachable");
        },
        (city, state, candidates) -> Optional.empty(), data -> BLOCKS, new RecordingPublisher());
    WeatherFunction function = new WeatherFunction(service);

    FakeHttpRequest request = new FakeHttpRequest("not json at all");
    FakeHttpResponse response = new FakeHttpResponse();

    function.service(request, response);

    assertEquals(500, response.statusCode());
  }

  @Test
  void publishFailureReturns500ForRedelivery() throws Exception {
    WeatherService service = new WeatherService(
        city -> List.of(LOCATION),
        (lat, lon) -> new ForecastData(List.of(), List.of(), List.of(), List.of(), List.of()),
        (city, state, candidates) -> Optional.of(LOCATION),
        data -> BLOCKS,
        new ResultPublisher() {
          @Override
          public void publishFetched(String requestId, ResolvedLocation location, ForecastBlocks blocks)
              throws PublishException {
            throw new PublishException("schema violation");
          }

          @Override
          public void publishFailed(String requestId, String reason) {}
        });
    WeatherFunction function = new WeatherFunction(service);

    FakeHttpRequest request = new FakeHttpRequest(pushEnvelope("req-3", payload("Ayer Hitam", "Johor")));
    FakeHttpResponse response = new FakeHttpResponse();

    function.service(request, response);

    assertEquals(500, response.statusCode());
  }

  private static String payload(String city, String state) {
    return "{\"city\":\"" + city + "\",\"state\":\"" + state + "\"}";
  }

  private static String pushEnvelope(String requestId, String payload) {
    String bodyJson = "{\"use_case\":\"WEATHER\",\"stage\":\"REQUESTED\",\"request_id\":\"" + requestId
        + "\",\"payload\":" + jsonString(payload) + ",\"metadata\":\"\"}";
    String encoded = Base64.getEncoder().encodeToString(bodyJson.getBytes(StandardCharsets.UTF_8));
    return "{\"message\":{\"data\":\"" + encoded + "\",\"messageId\":\"1\","
        + "\"attributes\":{\"use_case\":\"WEATHER\",\"stage\":\"REQUESTED\",\"request_id\":\"" + requestId + "\"}},"
        + "\"subscription\":\"projects/p/subscriptions/weather-svc-gateway-requests-sub\"}";
  }

  private static String jsonString(String raw) {
    return "\"" + raw.replace("\"", "\\\"") + "\"";
  }

  private static final class RecordingPublisher implements ResultPublisher {
    String fetchedRequestId;
    String failedRequestId;
    String failedReason;

    @Override
    public void publishFetched(String requestId, ResolvedLocation location, ForecastBlocks blocks) {
      this.fetchedRequestId = requestId;
    }

    @Override
    public void publishFailed(String requestId, String reason) {
      this.failedRequestId = requestId;
      this.failedReason = reason;
    }
  }
}
