package com.jameshskoh.weather.port;

import com.jameshskoh.weather.domain.ForecastData;

/**
 * Fetches the hourly forecast for a resolved location's coordinates via the open-meteo Forecast
 * API ({@code timezone=Asia/Kuala_Lumpur}, per docs/arch/open-meteo-integration.md).
 */
public interface Forecaster {

  ForecastData fetch(double latitude, double longitude) throws OpenMeteoException;
}
