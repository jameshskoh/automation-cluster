package com.jameshskoh.gateway.domain;

public record GatewayMessage(
    String useCase, String stage, String requestId, String payload, String metadata) {

  public GatewayMessage {
    if (useCase == null || useCase.isBlank()) {
      throw new IllegalArgumentException("useCase must not be null or blank");
    }
    if (stage == null || stage.isBlank()) {
      throw new IllegalArgumentException("stage must not be null or blank");
    }
    if (requestId == null || requestId.isBlank()) {
      throw new IllegalArgumentException("requestId must not be null or blank");
    }
    if (payload == null) {
      throw new IllegalArgumentException("payload must not be null");
    }
    if (metadata == null) {
      throw new IllegalArgumentException("metadata must not be null");
    }
  }
}
