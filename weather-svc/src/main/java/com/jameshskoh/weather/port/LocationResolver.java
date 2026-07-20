package com.jameshskoh.weather.port;

import com.jameshskoh.weather.domain.ResolvedLocation;

import java.util.List;
import java.util.Optional;

/**
 * Resolves a {@link Geocoder}'s candidates to the single best match for a city + state, or an empty
 * {@link Optional} on no match (not an exception). See docs/arch/open-meteo-integration.md, and
 * {@link com.jameshskoh.weather.resolve.LocationResolverImpl} for the filter/pick rules.
 */
public interface LocationResolver {

  Optional<ResolvedLocation> resolve(String city, String state, List<ResolvedLocation> candidates);
}
