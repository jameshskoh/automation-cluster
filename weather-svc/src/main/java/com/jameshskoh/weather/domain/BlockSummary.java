package com.jameshskoh.weather.domain;

/**
 * Per-block statistics for one 6-hour bucket (docs/arch/open-meteo-integration.md).
 *
 * @param block one of {@code "midnight"}, {@code "morning"}, {@code "afternoon"}, {@code "night"}
 * @param tempHigh / tempLow max/min {@code temperature_2m} over the block's usable hours
 * @param feelsLikeHigh / feelsLikeLow max/min {@code apparent_temperature} over the block's usable
 *     hours
 * @param rainProbability max {@code precipitation_probability} over the block's usable hours
 * @param skyCondition the dominant condition, from the block's maximum {@code weather_code}
 */
public record BlockSummary(
    String block,
    double tempHigh,
    double tempLow,
    double feelsLikeHigh,
    double feelsLikeLow,
    double rainProbability,
    SkyCondition skyCondition) {}
