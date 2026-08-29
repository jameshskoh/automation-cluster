package com.jameshskoh.weather.resolve;

import com.jameshskoh.weather.domain.ResolvedLocation;
import com.jameshskoh.weather.port.LocationResolver;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Deterministic client-side resolution over a {@link com.jameshskoh.weather.port.Geocoder}'s
 * candidates (see docs/arch/open-meteo-integration.md, "Client-side resolution"):
 *
 * <ol>
 *   <li>State filter — keep candidates whose {@code admin1} normalized-equals the requested state.
 *   <li>Fuzzy over-match rejection — require the candidate's {@code name} to normalized-exactly
 *       equal the requested city (rejects e.g. "Kampung Ayer Itam" for "Ayer Hitam").
 *   <li>Deterministic pick among survivors — highest population (missing = lowest priority),
 *       tiebreak lowest id.
 * </ol>
 */
public final class LocationResolverImpl implements LocationResolver {

  // Preferred-first ordering: descending population (absent = least preferred), then ascending id.
  private static final Comparator<ResolvedLocation> BEST_PICK_FIRST = (a, b) -> {
    long populationA = a.population() == null ? Long.MIN_VALUE : a.population();
    long populationB = b.population() == null ? Long.MIN_VALUE : b.population();
    if (populationA != populationB) {
      return Long.compare(populationB, populationA);
    }
    return Long.compare(a.id(), b.id());
  };

  @Override
  public Optional<ResolvedLocation> resolve(
      String city, String state, List<ResolvedLocation> candidates) {
    String normalizedCity = normalize(city);
    String normalizedState = normalize(state);

    List<ResolvedLocation> survivors = candidates.stream()
        .filter(candidate -> normalize(candidate.state()).equals(normalizedState))
        .filter(candidate -> normalize(candidate.name()).equals(normalizedCity))
        .sorted(BEST_PICK_FIRST)
        .toList();

    return survivors.isEmpty() ? Optional.empty() : Optional.of(survivors.get(0));
  }

  private static String normalize(String value) {
    return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
  }
}
