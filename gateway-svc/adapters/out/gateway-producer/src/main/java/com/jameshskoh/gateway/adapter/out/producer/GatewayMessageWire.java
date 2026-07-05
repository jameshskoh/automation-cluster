package com.jameshskoh.gateway.adapter.out.producer;

import com.jameshskoh.gateway.domain.GatewayMessage;

import com.fasterxml.jackson.annotation.JsonProperty;

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

  public static GatewayMessageWire from(GatewayMessage message) {
    return new GatewayMessageWire(
        message.useCase(), message.stage(), message.requestId(), message.payload(),
        message.metadata());
  }
}
