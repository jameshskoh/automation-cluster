package com.jameshskoh.weather.messaging.in;

import com.google.gson.annotations.SerializedName;

/**
 * The shared message envelope (see docs/arch/messaging.md), gson-serializable to/from its
 * snake_case on-the-wire JSON — one canonical type for both the inbound {@code REQUESTED} body and
 * the outbound {@code FETCHED}/{@code FAILED} messages.
 */
public record GatewayMessage(
    @SerializedName("use_case") String useCase,
    @SerializedName("stage") String stage,
    @SerializedName("request_id") String requestId,
    @SerializedName("payload") String payload,
    @SerializedName("metadata") String metadata) {}
