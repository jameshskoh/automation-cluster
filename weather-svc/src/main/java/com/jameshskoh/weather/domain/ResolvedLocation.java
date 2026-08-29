package com.jameshskoh.weather.domain;

/**
 * A location from the open-meteo Geocoding API — used both as a search candidate and, once picked,
 * as the location forecasted against. See docs/arch/open-meteo-integration.md.
 *
 * @param name the place name as returned by the API
 * @param state the {@code admin1} field (Malaysian state / federal territory)
 * @param population may be {@code null} — the API omits the key entirely when absent
 * @param id the open-meteo location id, always present
 */
public record ResolvedLocation(
    String name,
    String state,
    double latitude,
    double longitude,
    String timezone,
    Long population,
    long id) {}
