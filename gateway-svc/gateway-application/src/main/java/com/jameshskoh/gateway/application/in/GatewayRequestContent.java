package com.jameshskoh.gateway.application.in;

public record GatewayRequestContent(String metadata, String payload) {

  public GatewayRequestContent {
    if (metadata == null) {
      throw new IllegalArgumentException("metadata must not be null");
    }
    if (payload == null) {
      throw new IllegalArgumentException("payload must not be null");
    }
  }
}
