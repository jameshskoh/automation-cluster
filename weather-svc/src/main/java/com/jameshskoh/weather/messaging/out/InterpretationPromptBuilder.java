package com.jameshskoh.weather.messaging.out;

import com.jameshskoh.weather.domain.ResolvedLocation;

/**
 * Builds {@code FETCHED.metadata} — the interpretation prompt (see docs/arch/messaging.md,
 * "Interpretation prompt"): a 2-sentence summary (if and when it rains; feels-like high/low) plus a
 * compact one-row-per-block table (temp high/low, feels-like high/low, sky emoji), using only the
 * supplied data.
 */
final class InterpretationPromptBuilder {

  private InterpretationPromptBuilder() {}

  static String build(ResolvedLocation location) {
    return """
        Here is an aggregated weather forecast for %s, %s, Malaysia, supplied as JSON data \
        alongside this prompt. Using only the supplied data — do not invent anything:

        1. Write a 2-sentence summary covering (a) if and when it will rain, and (b) the day's \
        feels-like temperature high and low.
        2. Then produce a compact table with one row per block, in this order: midnight, \
        morning, afternoon, night. Columns: temp high/low, feels-like high/low, and the sky \
        emoji already supplied in the data for that block (render it as-is; do not classify the \
        weather yourself).

        Keep the prose minimal.\
        """.formatted(location.name(), location.state());
  }
}
