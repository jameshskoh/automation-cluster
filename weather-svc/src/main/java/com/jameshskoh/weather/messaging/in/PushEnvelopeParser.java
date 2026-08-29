package com.jameshskoh.weather.messaging.in;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * Parses the raw HTTP body Pub/Sub POSTs to the push subscription into a {@link GatewayMessage}:
 * base64-decodes {@code message.data} and gson-parses the envelope body. See docs/arch/messaging.md.
 *
 * <p>Throws on any anomaly (malformed JSON, missing {@code message.data}, or a body missing
 * {@code request_id}): these violate Pub/Sub's own push contract — not a business-level bad
 * request — and carry no {@code request_id} to address a {@code FAILED} reply to.
 */
public final class PushEnvelopeParser {

  private static final Gson GSON = new Gson();

  private PushEnvelopeParser() {}

  public static GatewayMessage parse(String rawBody) {
    PushEnvelopeDto envelope;
    try {
      envelope = GSON.fromJson(rawBody, PushEnvelopeDto.class);
    } catch (JsonSyntaxException e) {
      throw new IllegalArgumentException("Push envelope is not valid JSON", e);
    }
    if (envelope == null || envelope.message() == null || envelope.message().data() == null) {
      throw new IllegalArgumentException("Push envelope is missing message.data");
    }

    byte[] decoded;
    try {
      decoded = Base64.getDecoder().decode(envelope.message().data());
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("message.data is not valid base64", e);
    }
    String bodyJson = new String(decoded, StandardCharsets.UTF_8);

    GatewayMessage message;
    try {
      message = GSON.fromJson(bodyJson, GatewayMessage.class);
    } catch (JsonSyntaxException e) {
      throw new IllegalArgumentException("Decoded message body is not valid JSON", e);
    }
    if (message == null || message.requestId() == null || message.requestId().isBlank()) {
      throw new IllegalArgumentException("Decoded message body is missing request_id");
    }
    return message;
  }

  /**
   * The JSON Pub/Sub POSTs to a push subscription's endpoint (see docs/arch/messaging.md). Only the
   * fields weather-svc reads are declared; gson ignores the rest.
   */
  private record PushEnvelopeDto(MessageDto message, String subscription) {

    record MessageDto(String data, String messageId, Map<String, String> attributes) {}
  }
}
