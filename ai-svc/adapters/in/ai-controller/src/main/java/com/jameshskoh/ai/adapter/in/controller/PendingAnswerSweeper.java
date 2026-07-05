package com.jameshskoh.ai.adapter.in.controller;

import com.jameshskoh.ai.application.PendingAnswerRegistry;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically evicts stale entries from {@link PendingAnswerRegistry} whose answer never arrived.
 * Bounds map growth and, via each sink's {@code onExpire}, lets the Slack path post a timeout notice
 * while its {@code response_url} is still valid (Slack allows ~30 min / 5 uses).
 *
 * <p>HTTP long-poll entries are normally evicted by their own {@code DeferredResult} timeout well
 * before the TTL, so this sweeper is primarily a safety net for the Slack path and for answers that
 * are lost entirely.
 */
@Component
public class PendingAnswerSweeper {

  private static final Logger log = LoggerFactory.getLogger(PendingAnswerSweeper.class);

  private final PendingAnswerRegistry pendingAnswerRegistry;
  private final Duration ttl;

  public PendingAnswerSweeper(
      PendingAnswerRegistry pendingAnswerRegistry,
      @Value("${qa.pending.ttl-millis}") long ttlMillis) {
    this.pendingAnswerRegistry = pendingAnswerRegistry;
    this.ttl = Duration.ofMillis(ttlMillis);
  }

  @Scheduled(fixedRateString = "${qa.pending.sweep-interval-millis}")
  public void sweep() {
    int evicted = pendingAnswerRegistry.evictExpired(ttl);
    if (evicted > 0) {
      log.info("Evicted {} pending answer(s) past TTL of {}", evicted, ttl);
    }
  }
}
