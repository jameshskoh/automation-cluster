package com.jameshskoh.weather.domain;

import java.util.List;

/**
 * The raw hourly forecast arrays from the open-meteo Forecast API: one entry per hourly index, all
 * lists the same length as {@code time}. Values other than {@code time} may contain {@code null}
 * for a missing hour. See docs/arch/open-meteo-integration.md.
 *
 * @param time ISO-8601 local timestamps (already in {@code Asia/Kuala_Lumpur}, per the forecast
 *     query's {@code timezone} parameter)
 * @param temperature2m {@code temperature_2m}, degrees Celsius
 * @param apparentTemperature {@code apparent_temperature}, feels-like degrees Celsius
 * @param precipitationProbability {@code precipitation_probability}, percent
 * @param weatherCode {@code weather_code}, WMO code
 */
public record ForecastData(
    List<String> time,
    List<Double> temperature2m,
    List<Double> apparentTemperature,
    List<Double> precipitationProbability,
    List<Integer> weatherCode) {}
