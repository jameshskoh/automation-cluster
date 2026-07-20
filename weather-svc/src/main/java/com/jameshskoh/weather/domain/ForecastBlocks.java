package com.jameshskoh.weather.domain;

import java.util.List;

/**
 * The full aggregation output: one {@link DayBlocks} per calendar day in the fetched forecast
 * window (grouped by local date), each holding up to four {@link BlockSummary}s in
 * midnight/morning/afternoon/night order. A day may have fewer than four when a block had no usable
 * hours (omitted rather than emitted with nulls). See docs/arch/open-meteo-integration.md.
 */
public record ForecastBlocks(List<DayBlocks> days) {

  public record DayBlocks(String date, List<BlockSummary> blocks) {}
}
