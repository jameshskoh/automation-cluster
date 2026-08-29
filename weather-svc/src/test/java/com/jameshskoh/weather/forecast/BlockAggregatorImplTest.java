package com.jameshskoh.weather.forecast;

import com.jameshskoh.weather.domain.BlockSummary;
import com.jameshskoh.weather.domain.ForecastBlocks;
import com.jameshskoh.weather.domain.ForecastData;
import com.jameshskoh.weather.domain.SkyCondition;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the guide's live 2-day sample forecast fixture
 * (knowledgebase/tools/apis/open-meteo/open-meteo-weather-forecast-api.md).
 */
class BlockAggregatorImplTest {

  private final BlockAggregatorImpl aggregator = new BlockAggregatorImpl();

  @Test
  void bucketsTheGuidesSampleIntoFourBlocksPerDay() {
    ForecastData data = sampleForecastData();

    ForecastBlocks blocks = aggregator.aggregate(data);

    assertEquals(2, blocks.days().size());
    ForecastBlocks.DayBlocks day1 = blocks.days().get(0);
    assertEquals("2026-07-13", day1.date());
    assertEquals(4, day1.blocks().size());
    assertEquals(
        List.of("midnight", "morning", "afternoon", "night"),
        day1.blocks().stream().map(BlockSummary::block).toList());

    ForecastBlocks.DayBlocks day2 = blocks.days().get(1);
    assertEquals("2026-07-14", day2.date());
    assertEquals(4, day2.blocks().size());
  }

  @Test
  void midnightBlockOfDay1AggregatesHours0to5() {
    // hours 0-5: temps [26.6,26.2,26.0,25.6,25.5,25.2], feels-like [31.7,31.3,31.3,30.6,30.8,30.5],
    // rain prob [0,0,0,1,2,6], weather_code all 3 (Cloudy).
    ForecastData data = sampleForecastData();

    BlockSummary midnight = aggregator.aggregate(data).days().get(0).blocks().get(0);

    assertEquals("midnight", midnight.block());
    assertEquals(26.6, midnight.tempHigh());
    assertEquals(25.2, midnight.tempLow());
    assertEquals(31.7, midnight.feelsLikeHigh());
    assertEquals(30.5, midnight.feelsLikeLow());
    assertEquals(6.0, midnight.rainProbability());
    assertEquals(SkyCondition.CLOUDY, midnight.skyCondition());
  }

  @Test
  void afternoonBlockOfDay1PicksTheMostSevereWeatherCode() {
    // hours 12-17: weather_code [51,51,3,51,51,3] -> max is 51 (Drizzle), even though most hours
    // are plain cloudy (3).
    ForecastData data = sampleForecastData();

    BlockSummary afternoon = aggregator.aggregate(data).days().get(0).blocks().get(2);

    assertEquals("afternoon", afternoon.block());
    assertEquals(SkyCondition.DRIZZLE, afternoon.skyCondition());
    assertEquals(32.1, afternoon.tempHigh()); // max temperature_2m over hours 12-17
    assertEquals(37.7, afternoon.feelsLikeHigh()); // max apparent_temperature over hours 12-17
  }

  @Test
  void skipsMissingHoursAndOmitsABlockWithNoUsableHours() {
    List<String> time = List.of(
        "2026-07-13T00:00", "2026-07-13T01:00", "2026-07-13T02:00",
        "2026-07-13T03:00", "2026-07-13T04:00", "2026-07-13T05:00");
    List<Double> temps = new ArrayList<>(List.of(20.0, 21.0, 22.0, 23.0, 24.0, 25.0));
    List<Double> feelsLike = new ArrayList<>(List.of(20.0, 21.0, 22.0, 23.0, 24.0, 25.0));
    List<Double> rainProb = new ArrayList<>(List.of(0.0, 0.0, 0.0, 0.0, 0.0, 0.0));
    List<Integer> codes = new ArrayList<>(List.of(0, 0, 0, 0, 0, 0));
    // Blank out the entire midnight block (all six hours) to exercise "no usable hours -> omit".
    for (int i = 0; i < 6; i++) {
      temps.set(i, null);
    }
    ForecastData data = new ForecastData(time, temps, feelsLike, rainProb, codes);

    ForecastBlocks blocks = aggregator.aggregate(data);

    assertEquals(1, blocks.days().size());
    assertTrue(blocks.days().get(0).blocks().isEmpty());
  }

  @Test
  void skipsIndividualHoursWithMissingValuesWithinAnOtherwiseUsableBlock() {
    List<String> time = Arrays.asList(
        "2026-07-13T00:00", "2026-07-13T01:00", "2026-07-13T02:00",
        "2026-07-13T03:00", "2026-07-13T04:00", "2026-07-13T05:00");
    List<Double> temps = new ArrayList<>(List.of(20.0, 21.0, 22.0, 23.0, 24.0, 100.0));
    // Hour 5's temperature is missing but its weather_code isn't -> that hour must be skipped
    // entirely, so temp high must be 24.0 (hour 4), not 100.0.
    temps.set(5, null);
    List<Double> feelsLike = new ArrayList<>(List.of(20.0, 21.0, 22.0, 23.0, 24.0, 25.0));
    List<Double> rainProb = new ArrayList<>(List.of(0.0, 0.0, 0.0, 0.0, 0.0, 0.0));
    List<Integer> codes = new ArrayList<>(List.of(0, 0, 0, 0, 0, 0));
    ForecastData data = new ForecastData(time, temps, feelsLike, rainProb, codes);

    ForecastBlocks blocks = aggregator.aggregate(data);

    BlockSummary midnight = blocks.days().get(0).blocks().get(0);
    assertEquals(24.0, midnight.tempHigh());
  }

  private static ForecastData sampleForecastData() {
    List<String> time = List.of(
        "2026-07-13T00:00", "2026-07-13T01:00", "2026-07-13T02:00", "2026-07-13T03:00",
        "2026-07-13T04:00", "2026-07-13T05:00", "2026-07-13T06:00", "2026-07-13T07:00",
        "2026-07-13T08:00", "2026-07-13T09:00", "2026-07-13T10:00", "2026-07-13T11:00",
        "2026-07-13T12:00", "2026-07-13T13:00", "2026-07-13T14:00", "2026-07-13T15:00",
        "2026-07-13T16:00", "2026-07-13T17:00", "2026-07-13T18:00", "2026-07-13T19:00",
        "2026-07-13T20:00", "2026-07-13T21:00", "2026-07-13T22:00", "2026-07-13T23:00",
        "2026-07-14T00:00", "2026-07-14T01:00", "2026-07-14T02:00", "2026-07-14T03:00",
        "2026-07-14T04:00", "2026-07-14T05:00", "2026-07-14T06:00", "2026-07-14T07:00",
        "2026-07-14T08:00", "2026-07-14T09:00", "2026-07-14T10:00", "2026-07-14T11:00",
        "2026-07-14T12:00", "2026-07-14T13:00", "2026-07-14T14:00", "2026-07-14T15:00",
        "2026-07-14T16:00", "2026-07-14T17:00", "2026-07-14T18:00", "2026-07-14T19:00",
        "2026-07-14T20:00", "2026-07-14T21:00", "2026-07-14T22:00", "2026-07-14T23:00");
    List<Double> temperature2m = doubles(
        26.6, 26.2, 26.0, 25.6, 25.5, 25.2, 24.5, 24.3, 24.8, 26.2, 27.8, 29.5,
        30.7, 31.7, 32.1, 31.1, 31.5, 31.4, 31.1, 30.1, 28.9, 28.5, 28.0, 27.5,
        27.2, 26.5, 26.2, 25.9, 25.6, 25.2, 25.1, 24.9, 25.4, 26.9, 28.8, 30.3,
        31.1, 31.9, 30.5, 28.6, 29.6, 30.0, 29.6, 29.0, 28.3, 27.8, 27.4, 27.0);
    List<Double> apparentTemperature = doubles(
        31.7, 31.3, 31.3, 30.6, 30.8, 30.5, 28.9, 29.6, 29.9, 31.6, 32.8, 33.8,
        35.6, 37.7, 37.2, 36.2, 35.8, 36.1, 35.5, 34.9, 34.8, 34.6, 33.9, 33.7,
        33.2, 31.8, 31.6, 31.2, 30.7, 30.6, 30.1, 29.9, 30.8, 32.0, 33.2, 34.1,
        35.2, 37.6, 35.4, 33.5, 34.4, 35.0, 34.6, 34.0, 33.9, 33.5, 33.2, 33.1);
    List<Double> precipitationProbability = doubles(
        0, 0, 0, 1, 2, 6, 14, 24, 31, 30, 26, 24,
        29, 37, 41, 37, 28, 20, 12, 5, 0, 0, 0, 2,
        6, 11, 16, 23, 30, 35, 36, 35, 33, 28, 22, 22,
        34, 52, 65, 69, 67, 61, 47, 27, 12, 5, 2, 2);
    List<Integer> weatherCode = List.of(
        3, 3, 3, 3, 3, 3, 51, 53, 3, 3, 3, 3,
        51, 51, 3, 51, 51, 3, 3, 3, 3, 3, 3, 3,
        3, 3, 3, 3, 3, 1, 1, 3, 2, 2, 2, 51,
        51, 51, 51, 55, 51, 3, 51, 3, 3, 2, 2, 2);
    return new ForecastData(time, temperature2m, apparentTemperature, precipitationProbability, weatherCode);
  }

  private static List<Double> doubles(double... values) {
    List<Double> list = new ArrayList<>();
    for (double v : values) {
      list.add(v);
    }
    return list;
  }
}
