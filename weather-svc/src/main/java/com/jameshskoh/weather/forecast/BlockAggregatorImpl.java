package com.jameshskoh.weather.forecast;

import com.jameshskoh.weather.domain.BlockSummary;
import com.jameshskoh.weather.domain.ForecastBlocks;
import com.jameshskoh.weather.domain.ForecastData;
import com.jameshskoh.weather.port.BlockAggregator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Buckets {@link ForecastData}'s hourly arrays into four 6-hour blocks per local day (see
 * docs/arch/open-meteo-integration.md, "Blocks - four 6-hour buckets" and "Per-block statistics"):
 * boundaries at 00/06/12/18 local time, no wraparound. Hours with a missing value are skipped; a
 * block with no usable hours is omitted rather than emitted with nulls.
 */
public final class BlockAggregatorImpl implements BlockAggregator {

  private static final List<String> BLOCK_ORDER = List.of("midnight", "morning", "afternoon", "night");

  @Override
  public ForecastBlocks aggregate(ForecastData data) {
    // date ("yyyy-MM-dd") -> block name -> hourly indices, insertion-ordered so output days/blocks
    // come out in the order hours were seen (already chronological in the open-meteo response).
    Map<String, Map<String, List<Integer>>> indicesByDateThenBlock = new LinkedHashMap<>();

    for (int i = 0; i < data.time().size(); i++) {
      String timestamp = data.time().get(i);
      String date = timestamp.substring(0, 10);
      int hour = Integer.parseInt(timestamp.substring(11, 13));
      String block = blockFor(hour);
      indicesByDateThenBlock
          .computeIfAbsent(date, d -> new LinkedHashMap<>())
          .computeIfAbsent(block, b -> new ArrayList<>())
          .add(i);
    }

    List<ForecastBlocks.DayBlocks> days = new ArrayList<>();
    for (Map.Entry<String, Map<String, List<Integer>>> dateEntry : indicesByDateThenBlock.entrySet()) {
      List<BlockSummary> blocks = new ArrayList<>();
      for (String block : BLOCK_ORDER) {
        List<Integer> indices = dateEntry.getValue().getOrDefault(block, List.of());
        summarize(block, indices, data).ifPresent(blocks::add);
      }
      days.add(new ForecastBlocks.DayBlocks(dateEntry.getKey(), blocks));
    }
    return new ForecastBlocks(days);
  }

  private Optional<BlockSummary> summarize(String block, List<Integer> indices, ForecastData data) {
    List<Integer> usable = indices.stream().filter(i -> hasUsableValues(i, data)).toList();
    if (usable.isEmpty()) {
      return Optional.empty();
    }

    double tempHigh = Double.NEGATIVE_INFINITY;
    double tempLow = Double.POSITIVE_INFINITY;
    double feelsLikeHigh = Double.NEGATIVE_INFINITY;
    double feelsLikeLow = Double.POSITIVE_INFINITY;
    double rainProbability = Double.NEGATIVE_INFINITY;
    int maxWeatherCode = Integer.MIN_VALUE;

    for (int i : usable) {
      double temp = data.temperature2m().get(i);
      double feelsLike = data.apparentTemperature().get(i);
      double rain = data.precipitationProbability().get(i);
      int code = data.weatherCode().get(i);

      tempHigh = Math.max(tempHigh, temp);
      tempLow = Math.min(tempLow, temp);
      feelsLikeHigh = Math.max(feelsLikeHigh, feelsLike);
      feelsLikeLow = Math.min(feelsLikeLow, feelsLike);
      rainProbability = Math.max(rainProbability, rain);
      maxWeatherCode = Math.max(maxWeatherCode, code);
    }

    return Optional.of(new BlockSummary(
        block, tempHigh, tempLow, feelsLikeHigh, feelsLikeLow, rainProbability,
        WmoCodeMapper.map(maxWeatherCode)));
  }

  private static boolean hasUsableValues(int i, ForecastData data) {
    return data.temperature2m().get(i) != null
        && data.apparentTemperature().get(i) != null
        && data.precipitationProbability().get(i) != null
        && data.weatherCode().get(i) != null;
  }

  private static String blockFor(int hour) {
    if (hour < 6) return "midnight";
    if (hour < 12) return "morning";
    if (hour < 18) return "afternoon";
    return "night";
  }
}
