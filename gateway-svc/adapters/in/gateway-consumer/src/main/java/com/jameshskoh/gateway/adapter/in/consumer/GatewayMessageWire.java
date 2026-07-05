package com.jameshskoh.gateway.adapter.in.consumer;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.jameshskoh.gateway.domain.GatewayMessage;

/**
 * The on-the-wire JSON shape of {@link GatewayMessage}, matching the snake_case field names
 * required by the shared envelope schema (see docs/arch/messaging.md and
 * schemas/gateway_message.proto). Mapping between this and the domain type is this adapter's
 * responsibility, not domain's — domain stays framework/serialization-free.
 */
public record GatewayMessageWire(
    @JsonProperty("use_case") String useCase,
    @JsonProperty("stage") String stage,
    @JsonProperty("request_id") String requestId,
    @JsonProperty("payload") String payload,
    @JsonProperty("metadata") String metadata) {

  public GatewayMessage toDomain() {
    return new GatewayMessage(useCase, stage, requestId, payload, metadata);
  }
}
