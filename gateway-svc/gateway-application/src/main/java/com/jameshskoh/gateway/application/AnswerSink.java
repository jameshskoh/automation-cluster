package com.jameshskoh.gateway.application;

import com.jameshskoh.gateway.domain.GatewayMessage;

/**
 * A framework-free delivery target for an asynchronous answer. Registered against a
 * {@code requestId} in {@link PendingAnswerRegistry}; invoked once the correlated
 * {@link GatewayMessage} arrives (via {@link #deliver}) or once the registration outlives its TTL
 * without an answer (via {@link #onExpire}).
 *
 * <p>Adapters supply the concrete sink: the HTTP long-poll path completes a future; the Slack path
 * posts to the channel. Keeping this an application-level abstraction lets a single subscriber path
 * serve both without the registry knowing about any framework type.
 */
@FunctionalInterface
public interface AnswerSink {

  /** Deliver the answer to whoever is waiting. Must not throw; the registry logs and swallows. */
  void deliver(GatewayMessage message);

  /**
   * Called when the registration is evicted by TTL before an answer arrived. Default is a no-op
   * (the HTTP path has already returned a timeout to its caller); the Slack path overrides this to
   * post a timeout notice while its {@code response_url} is still valid.
   */
  default void onExpire() {
    // no-op
  }
}
