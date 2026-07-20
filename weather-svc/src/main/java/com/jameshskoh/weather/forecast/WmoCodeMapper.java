package com.jameshskoh.weather.forecast;

import com.jameshskoh.weather.domain.SkyCondition;

/**
 * WMO {@code weather_code} -> {@link SkyCondition}, per docs/arch/open-meteo-integration.md.
 * Snow codes (71-77, 85, 86) are not applicable to Malaysia; like any other unmapped code, they
 * fall back to the neutral default ({@link SkyCondition#CLOUDY}) the doc allows.
 */
public final class WmoCodeMapper {

  private WmoCodeMapper() {}

  public static SkyCondition map(int weatherCode) {
    return switch (weatherCode) {
      case 0 -> SkyCondition.SUNNY;
      case 1, 2, 3 -> SkyCondition.CLOUDY;
      case 45, 48 -> SkyCondition.FOG;
      case 51, 53, 55, 56, 57 -> SkyCondition.DRIZZLE;
      case 61, 63, 65, 66, 67 -> SkyCondition.RAIN;
      case 80, 81, 82 -> SkyCondition.RAIN_SHOWERS;
      case 95, 96, 99 -> SkyCondition.THUNDERSTORM;
      default -> SkyCondition.CLOUDY;
    };
  }
}
