package com.jameshskoh.weather.messaging.out;

import com.jameshskoh.weather.domain.BlockSummary;
import com.jameshskoh.weather.domain.ForecastBlocks;
import com.jameshskoh.weather.domain.ResolvedLocation;

import java.util.List;

/**
 * The aggregated weather JSON shape published in {@code FETCHED.payload} (see
 * docs/arch/messaging.md, "FETCHED"): resolved location (name, state, coordinates, timezone) plus
 * the forecast blocks, each carrying the sky condition's description and emoji.
 */
record FetchedPayload(LocationDto location, List<BlockDto> blocks) {

  static FetchedPayload from(ResolvedLocation location, ForecastBlocks forecastBlocks) {
    LocationDto locationDto = new LocationDto(
        location.name(), location.state(), location.latitude(), location.longitude(),
        location.timezone());
    List<BlockDto> blockDtos = forecastBlocks.days().stream()
        .flatMap(day -> day.blocks().stream().map(block -> BlockDto.from(day.date(), block)))
        .toList();
    return new FetchedPayload(locationDto, blockDtos);
  }

  record LocationDto(String name, String state, double latitude, double longitude, String timezone) {}

  record BlockDto(
      String date,
      String block,
      double tempHigh,
      double tempLow,
      double feelsLikeHigh,
      double feelsLikeLow,
      double rainProbability,
      SkyDto sky) {

    static BlockDto from(String date, BlockSummary block) {
      return new BlockDto(
          date, block.block(), block.tempHigh(), block.tempLow(), block.feelsLikeHigh(),
          block.feelsLikeLow(), block.rainProbability(),
          new SkyDto(block.skyCondition().description(), block.skyCondition().emoji()));
    }
  }

  record SkyDto(String description, String emoji) {}
}
