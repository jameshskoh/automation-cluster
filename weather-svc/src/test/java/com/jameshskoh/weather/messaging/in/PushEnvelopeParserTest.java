package com.jameshskoh.weather.messaging.in;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PushEnvelopeParserTest {

  @Test
  void parsesTheDocumentedPushEnvelopeShape() {
    String bodyJson = """
        {"use_case":"WEATHER","stage":"REQUESTED","request_id":"abc-123",\
        "payload":"{\\"city\\":\\"Ayer Hitam\\",\\"state\\":\\"Johor\\"}","metadata":""}""";
    String encoded = Base64.getEncoder().encodeToString(bodyJson.getBytes(StandardCharsets.UTF_8));
    String pushEnvelope = """
        {
          "message": {
            "data": "%s",
            "messageId": "1",
            "attributes": {"use_case": "WEATHER", "stage": "REQUESTED", "request_id": "abc-123"}
          },
          "subscription": "projects/p/subscriptions/weather-svc-gateway-requests-sub"
        }""".formatted(encoded);

    GatewayMessage message = PushEnvelopeParser.parse(pushEnvelope);

    assertEquals("WEATHER", message.useCase());
    assertEquals("REQUESTED", message.stage());
    assertEquals("abc-123", message.requestId());
    assertEquals("{\"city\":\"Ayer Hitam\",\"state\":\"Johor\"}", message.payload());
  }

  @Test
  void throwsOnNonJsonBody() {
    assertThrows(IllegalArgumentException.class, () -> PushEnvelopeParser.parse("not json"));
  }

  @Test
  void throwsWhenMessageDataMissing() {
    assertThrows(IllegalArgumentException.class,
        () -> PushEnvelopeParser.parse("{\"subscription\":\"projects/p/subscriptions/s\"}"));
  }

  @Test
  void throwsWhenDecodedBodyMissingRequestId() {
    String bodyJson = "{\"use_case\":\"WEATHER\",\"stage\":\"REQUESTED\",\"payload\":\"{}\",\"metadata\":\"\"}";
    String encoded = Base64.getEncoder().encodeToString(bodyJson.getBytes(StandardCharsets.UTF_8));
    String pushEnvelope = "{\"message\":{\"data\":\"" + encoded + "\"}}";

    assertThrows(IllegalArgumentException.class, () -> PushEnvelopeParser.parse(pushEnvelope));
  }
}
