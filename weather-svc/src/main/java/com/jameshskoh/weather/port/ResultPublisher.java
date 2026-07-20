package com.jameshskoh.weather.port;

import com.jameshskoh.weather.domain.ForecastBlocks;
import com.jameshskoh.weather.domain.ResolvedLocation;

/**
 * Publishes weather-svc's one outcome per request to {@code weather-svc-results}: either the
 * success stage {@code FETCHED} (aggregated data + interpretation prompt) or the terminal error
 * stage {@code FAILED} (a human-readable reason). See docs/arch/messaging.md, "Outbound messages".
 */
public interface ResultPublisher {

  void publishFetched(String requestId, ResolvedLocation location, ForecastBlocks blocks)
      throws PublishException;

  void publishFailed(String requestId, String reason) throws PublishException;
}
