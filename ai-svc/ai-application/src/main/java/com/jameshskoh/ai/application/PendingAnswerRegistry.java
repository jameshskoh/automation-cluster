package com.jameshskoh.ai.application;

import com.jameshskoh.ai.domain.AiResponse;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Correlates in-flight async questions (by {@code uuid}) with the {@link AnswerSink} that should
 * receive the eventual answer. Both the HTTP long-poll path and the Slack path register here; the
 * response subscriber drives {@link #complete} regardless of which inbound adapter asked.
 */
public class PendingAnswerRegistry {

  private static final Logger log = LoggerFactory.getLogger(PendingAnswerRegistry.class);

  private final ConcurrentMap<String, Pending> pending = new ConcurrentHashMap<>();

  private record Pending(AnswerSink sink, Instant registeredAt) {
  }

  /** Register a sink for {@code uuid}. Must happen before publishing to avoid a lost fast answer. */
  public void register(String uuid, AnswerSink sink) {
    pending.put(uuid, new Pending(sink, Instant.now()));
  }

  public void complete(String uuid, AiResponse response) {
    Pending removed = pending.remove(uuid);
    if (removed != null) {
      deliver(uuid, removed.sink(), response);
    } else {
      // No sink registered for this uuid: it already timed out and was evicted, or the answer
      // arrived on an instance that never registered it (single-instance assumption — see the
      // async flow docs). Surface it rather than dropping silently.
      log.warn("Received answer for unknown or already-evicted uuid={}, dropping", uuid);
    }
  }

  public void evict(String uuid) {
    pending.remove(uuid);
  }

  /**
   * Remove every registration older than {@code ttl} and invoke {@link AnswerSink#onExpire()} on
   * each. Bounds map growth for answers that never arrive and lets the Slack path notify its caller
   * while the {@code response_url} is still valid. Returns the number evicted.
   */
  public int evictExpired(Duration ttl) {
    Instant cutoff = Instant.now().minus(ttl);
    List<AnswerSink> expired = new ArrayList<>();
    for (Iterator<Map.Entry<String, Pending>> it = pending.entrySet().iterator(); it.hasNext(); ) {
      Map.Entry<String, Pending> entry = it.next();
      if (entry.getValue().registeredAt().isBefore(cutoff)) {
        expired.add(entry.getValue().sink());
        it.remove();
      }
    }
    for (AnswerSink sink : expired) {
      try {
        sink.onExpire();
      } catch (Exception e) {
        log.error("AnswerSink.onExpire threw during eviction sweep", e);
      }
    }
    return expired.size();
  }

  private void deliver(String uuid, AnswerSink sink, AiResponse response) {
    try {
      sink.deliver(response);
    } catch (Exception e) {
      // A delivery failure (e.g. Slack respond error) must not break the subscriber's ack path.
      log.error("Failed to deliver answer for uuid={}", uuid, e);
    }
  }
}
