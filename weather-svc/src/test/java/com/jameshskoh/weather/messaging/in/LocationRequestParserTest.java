package com.jameshskoh.weather.messaging.in;

import com.jameshskoh.weather.domain.LocationRequest;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocationRequestParserTest {

  @Test
  void parsesTheDocumentedPayloadShape() {
    Optional<LocationRequest> result =
        LocationRequestParser.parse("{\"city\": \"Ayer Hitam\", \"state\": \"Johor\"}");

    assertEquals(Optional.of(new LocationRequest("Ayer Hitam", "Johor")), result);
  }

  @Test
  void emptyOnMissingState() {
    assertTrue(LocationRequestParser.parse("{\"city\": \"Ayer Hitam\"}").isEmpty());
  }

  @Test
  void emptyOnBlankCity() {
    assertTrue(LocationRequestParser.parse("{\"city\": \"  \", \"state\": \"Johor\"}").isEmpty());
  }

  @Test
  void emptyOnNonJson() {
    assertTrue(LocationRequestParser.parse("not json").isEmpty());
  }

  @Test
  void emptyOnNullOrBlankPayload() {
    assertTrue(LocationRequestParser.parse(null).isEmpty());
    assertTrue(LocationRequestParser.parse("").isEmpty());
    assertTrue(LocationRequestParser.parse("   ").isEmpty());
  }
}
