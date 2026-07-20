package com.jameshskoh.weather.messaging.in;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.jameshskoh.weather.domain.LocationRequest;

import java.util.Optional;

/**
 * Parses {@code REQUESTED.payload} into a {@link LocationRequest} (see docs/arch/messaging.md):
 * {@code {"city": "...", "state": "..."}}. Returns an empty {@link Optional} rather than throwing
 * on anything that doesn't decode into that shape — a business-level bad request, not the
 * envelope-contract failure {@link PushEnvelopeParser} guards.
 */
public final class LocationRequestParser {

  private static final Gson GSON = new Gson();

  private LocationRequestParser() {}

  public static Optional<LocationRequest> parse(String payloadJson) {
    if (payloadJson == null || payloadJson.isBlank()) {
      return Optional.empty();
    }
    LocationRequestDto dto;
    try {
      dto = GSON.fromJson(payloadJson, LocationRequestDto.class);
    } catch (JsonSyntaxException e) {
      return Optional.empty();
    }
    if (dto == null || isBlank(dto.city()) || isBlank(dto.state())) {
      return Optional.empty();
    }
    return Optional.of(new LocationRequest(dto.city().trim(), dto.state().trim()));
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private record LocationRequestDto(String city, String state) {}
}
