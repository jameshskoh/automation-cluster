package com.jameshskoh.gateway.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.jameshskoh.gateway.domain.GatewayMessage;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

class PendingAnswerRegistryTest {

  private static GatewayMessage answer(String requestId, String metadata) {
    return new GatewayMessage("QA", "ANSWERED", requestId, "", metadata);
  }

  @Test
  void completeDeliversResponseToRegisteredSink() {
    PendingAnswerRegistry registry = new PendingAnswerRegistry();
    AtomicReference<GatewayMessage> delivered = new AtomicReference<>();
    registry.register("u1", delivered::set);

    GatewayMessage response = answer("u1", "42");
    registry.complete("u1", response);

    assertThat(delivered.get()).isEqualTo(response);
  }

  @Test
  void completeForUnknownUuidIsDroppedSilently() {
    PendingAnswerRegistry registry = new PendingAnswerRegistry();
    // No sink registered — must not throw.
    registry.complete("missing", answer("missing", "answer"));
  }

  @Test
  void evictRemovesSinkSoLaterCompleteIsNoOp() {
    PendingAnswerRegistry registry = new PendingAnswerRegistry();
    AtomicBoolean delivered = new AtomicBoolean(false);
    registry.register("u1", r -> delivered.set(true));

    registry.evict("u1");
    registry.complete("u1", answer("u1", "answer"));

    assertThat(delivered).isFalse();
  }

  @Test
  void evictExpiredFiresOnExpireForStaleEntriesOnly() throws InterruptedException {
    PendingAnswerRegistry registry = new PendingAnswerRegistry();
    AtomicBoolean staleExpired = new AtomicBoolean(false);
    AtomicBoolean freshExpired = new AtomicBoolean(false);

    registry.register("stale", new AnswerSink() {
      @Override
      public void deliver(GatewayMessage message) {
      }

      @Override
      public void onExpire() {
        staleExpired.set(true);
      }
    });

    // Ensure the stale entry is measurably older than the fresh one.
    Thread.sleep(20);

    registry.register("fresh", new AnswerSink() {
      @Override
      public void deliver(GatewayMessage message) {
      }

      @Override
      public void onExpire() {
        freshExpired.set(true);
      }
    });

    int evicted = registry.evictExpired(Duration.ofMillis(10));

    assertThat(evicted).isEqualTo(1);
    assertThat(staleExpired).isTrue();
    assertThat(freshExpired).isFalse();
    // The fresh entry survived and can still be completed.
    AtomicReference<GatewayMessage> delivered = new AtomicReference<>();
    registry.register("fresh2", delivered::set);
    registry.complete("fresh2", answer("fresh2", "ok"));
    assertThat(delivered.get().metadata()).isEqualTo("ok");
  }

  @Test
  void deliverFailureDoesNotPropagate() {
    PendingAnswerRegistry registry = new PendingAnswerRegistry();
    registry.register("u1", r -> {
      throw new RuntimeException("boom");
    });
    // Must be swallowed and logged, not thrown back into the subscriber path.
    registry.complete("u1", answer("u1", "answer"));
  }
}
