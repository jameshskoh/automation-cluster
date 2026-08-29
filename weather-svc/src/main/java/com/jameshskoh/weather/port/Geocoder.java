package com.jameshskoh.weather.port;

import com.jameshskoh.weather.domain.ResolvedLocation;

import java.util.List;

/**
 * Resolves a city name to candidate locations via the open-meteo Geocoding API
 * ({@code countryCode=MY}, {@code count=10}). Returns an empty list on a genuine no-match (HTTP
 * 200 with no results) — a transient failure (5xx/network/timeout) is a thrown {@link
 * OpenMeteoException} instead, per docs/arch/open-meteo-integration.md.
 */
public interface Geocoder {

  List<ResolvedLocation> search(String city) throws OpenMeteoException;
}
