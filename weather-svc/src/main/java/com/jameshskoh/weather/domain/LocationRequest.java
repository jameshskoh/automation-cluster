package com.jameshskoh.weather.domain;

/**
 * The inbound location request: a Malaysian city + state, parsed from {@code REQUESTED.payload}
 * (docs/arch/messaging.md).
 */
public record LocationRequest(String city, String state) {}
