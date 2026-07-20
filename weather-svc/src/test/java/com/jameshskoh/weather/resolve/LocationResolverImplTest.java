package com.jameshskoh.weather.resolve;

import com.jameshskoh.weather.domain.ResolvedLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the guide's live 5-result "Ayer Hitam" fixture (docs/arch/open-meteo-integration.md /
 * knowledgebase/tools/apis/open-meteo/open-meteo-geocoding-api.md).
 */
class LocationResolverImplTest {

  private static final ResolvedLocation JOHOR =
      new ResolvedLocation("Ayer Hitam", "Johor", 1.915, 103.1808, "Asia/Kuala_Lumpur", 6745L, 1732696);
  private static final ResolvedLocation NEGERI_SEMBILAN =
      new ResolvedLocation("Ayer Hitam", "Negeri Sembilan", 2.9396, 102.3968, "Asia/Kuala_Lumpur", null, 1734828);
  private static final ResolvedLocation KEDAH_KUBANG_PASU =
      new ResolvedLocation("Ayer Hitam", "Kedah", 6.24086, 100.25352, "Asia/Kuala_Lumpur", 5000L, 1736300);
  private static final ResolvedLocation KEDAH_BANDAR_BAHARU =
      new ResolvedLocation("Ayer Hitam", "Kedah", 5.21295, 100.61704, "Asia/Kuala_Lumpur", 8000L, 1771514);
  private static final ResolvedLocation PENANG_OVER_MATCH =
      new ResolvedLocation("Kampung Ayer Itam", "Penang", 5.4549, 100.45362, "Asia/Kuala_Lumpur", 20000L, 1782415);

  private static final List<ResolvedLocation> FIVE_RESULT_FIXTURE = List.of(
      JOHOR, NEGERI_SEMBILAN, KEDAH_KUBANG_PASU, KEDAH_BANDAR_BAHARU, PENANG_OVER_MATCH);

  private final LocationResolverImpl resolver = new LocationResolverImpl();

  @Test
  void filtersToRequestedStateAndRejectsFuzzyOverMatch() {
    // Penang has only the fuzzy over-match ("Kampung Ayer Itam") for the exact name "Ayer Hitam" —
    // must not resolve.
    Optional<ResolvedLocation> result = resolver.resolve("Ayer Hitam", "Penang", FIVE_RESULT_FIXTURE);

    assertTrue(result.isEmpty());
  }

  @Test
  void resolvesTheSingleSurvivorInJohor() {
    Optional<ResolvedLocation> result = resolver.resolve("Ayer Hitam", "Johor", FIVE_RESULT_FIXTURE);

    assertEquals(Optional.of(JOHOR), result);
  }

  @Test
  void picksHighestPopulationAmongTwoKedahSurvivors() {
    Optional<ResolvedLocation> result = resolver.resolve("Ayer Hitam", "Kedah", FIVE_RESULT_FIXTURE);

    // Bandar Baharu (8000) > Kubang Pasu (5000).
    assertEquals(Optional.of(KEDAH_BANDAR_BAHARU), result);
  }

  @Test
  void tiebreaksOnLowestIdWhenPopulationEqual() {
    ResolvedLocation lowerIdSamePopulation =
        new ResolvedLocation("Ayer Hitam", "Kedah", 6.24086, 100.25352, "Asia/Kuala_Lumpur", 8000L, 1000000);
    List<ResolvedLocation> candidates = List.of(KEDAH_BANDAR_BAHARU, lowerIdSamePopulation);

    Optional<ResolvedLocation> result = resolver.resolve("Ayer Hitam", "Kedah", candidates);

    assertEquals(Optional.of(lowerIdSamePopulation), result);
  }

  @Test
  void missingPopulationIsTreatedAsLowestPriority() {
    List<ResolvedLocation> candidates = List.of(NEGERI_SEMBILAN); // sole Negeri Sembilan survivor, no population

    Optional<ResolvedLocation> result = resolver.resolve("Ayer Hitam", "Negeri Sembilan", candidates);

    assertEquals(Optional.of(NEGERI_SEMBILAN), result);
  }

  @Test
  void stateAndCityMatchingIsCaseAndWhitespaceInsensitive() {
    Optional<ResolvedLocation> result = resolver.resolve("  ayer hitam ", " JOHOR", FIVE_RESULT_FIXTURE);

    assertEquals(Optional.of(JOHOR), result);
  }

  @Test
  void noSurvivorInAnUnrepresentedStateIsANoMatch() {
    Optional<ResolvedLocation> result = resolver.resolve("Ayer Hitam", "Melaka", FIVE_RESULT_FIXTURE);

    assertTrue(result.isEmpty());
  }
}
