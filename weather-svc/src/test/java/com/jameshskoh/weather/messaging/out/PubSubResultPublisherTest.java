package com.jameshskoh.weather.messaging.out;

import com.jameshskoh.weather.domain.BlockSummary;
import com.jameshskoh.weather.domain.ForecastBlocks;
import com.jameshskoh.weather.domain.ResolvedLocation;
import com.jameshskoh.weather.domain.SkyCondition;
import com.jameshskoh.weather.messaging.in.GatewayMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises envelope-building without a live Pub/Sub client — {@link
 * com.google.cloud.pubsub.v1.Publisher} itself is out of scope for a unit test (no emulator/creds
 * available).
 */
class PubSubResultPublisherTest {

  private static final ResolvedLocation LOCATION = new ResolvedLocation(
      "Ayer Hitam", "Johor", 1.915, 103.1808, "Asia/Kuala_Lumpur", 6745L, 1732696);

  @Test
  void buildsFetchedMessageWithUseCaseStageAndEchoedRequestId() {
    ForecastBlocks blocks = new ForecastBlocks(List.of(new ForecastBlocks.DayBlocks(
        "2026-07-19",
        List.of(new BlockSummary("midnight", 26.0, 24.0, 30.0, 28.0, 10.0, SkyCondition.CLOUDY)))));

    GatewayMessage message = PubSubResultPublisher.buildFetchedMessage("req-1", LOCATION, blocks);

    assertEquals("WEATHER", message.useCase());
    assertEquals("FETCHED", message.stage());
    assertEquals("req-1", message.requestId());
    assertTrue(message.payload().contains("\"Ayer Hitam\""));
    assertTrue(message.payload().contains("midnight"));
    assertTrue(message.payload().contains("☁️"));
    assertTrue(message.metadata().contains("Ayer Hitam"));
    assertTrue(message.metadata().contains("midnight, morning, afternoon, night"));
  }

  @Test
  void buildsFailedMessageWithEmptyPayloadAndReasonInMetadata() {
    GatewayMessage message = PubSubResultPublisher.buildFailedMessage(
        "req-2", "no match for \"Ayer Hitam\" in \"Melaka\", Malaysia");

    assertEquals("WEATHER", message.useCase());
    assertEquals("FAILED", message.stage());
    assertEquals("req-2", message.requestId());
    assertEquals("", message.payload());
    assertEquals("no match for \"Ayer Hitam\" in \"Melaka\", Malaysia", message.metadata());
  }
}
