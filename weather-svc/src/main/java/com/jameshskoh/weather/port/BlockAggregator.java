package com.jameshskoh.weather.port;

import com.jameshskoh.weather.domain.ForecastBlocks;
import com.jameshskoh.weather.domain.ForecastData;

/**
 * Buckets raw hourly forecast data into four 6-hour blocks per day (midnight/morning/afternoon/
 * night) with per-block statistics and a dominant {@link com.jameshskoh.weather.domain.SkyCondition}.
 * See docs/arch/open-meteo-integration.md.
 */
public interface BlockAggregator {

  ForecastBlocks aggregate(ForecastData data);
}
