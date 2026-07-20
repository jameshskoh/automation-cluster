package com.jameshskoh.weather.domain;

/**
 * The outcome of processing one {@code REQUESTED} message: either a resolved location + aggregated
 * forecast blocks (published as {@code FETCHED}), or a human-readable reason (published as the
 * terminal {@code FAILED}). See docs/architecture.md, "Error posture".
 */
public sealed interface WeatherResult {

  record Fetched(ResolvedLocation location, ForecastBlocks blocks) implements WeatherResult {}

  record Failed(String reason) implements WeatherResult {}
}
