package com.jameshskoh.weather.domain;

/**
 * A sky classification carrying both a human-readable description and an emoji, so a downstream
 * consumer renders it as-is with no weather logic of its own. See
 * docs/arch/open-meteo-integration.md.
 */
public enum SkyCondition {
  SUNNY("Sunny / clear", "☀️"),
  CLOUDY("Cloudy", "☁️"),
  FOG("Fog", "🌫️"),
  DRIZZLE("Drizzle", "🌦️"),
  RAIN("Rain", "🌧️"),
  RAIN_SHOWERS("Rain showers", "🌧️"),
  THUNDERSTORM("Thunderstorm", "⛈️");

  private final String description;
  private final String emoji;

  SkyCondition(String description, String emoji) {
    this.description = description;
    this.emoji = emoji;
  }

  public String description() {
    return description;
  }

  public String emoji() {
    return emoji;
  }
}
