package com.jameshskoh.gateway.application.in;

import com.jameshskoh.gateway.application.AnswerSink;

public interface AsyncQuestionUseCase {

  /**
   * Registers {@code sink} against a freshly generated request id, then publishes the request. The
   * sink receives the answer whenever it arrives (delivered via the response subscriber).
   * Registration happens before publishing so a fast round-trip cannot complete the answer before
   * the sink exists. Returns the request id correlating the request with its eventual answer.
   *
   * <p>The sink is caller-supplied so this one primitive serves every inbound adapter: the HTTP
   * long-poll wraps a future, the Slack command posts back to its channel. Nothing here is tied to
   * any particular delivery channel.
   */
  String ask(GatewayRequestContent content, AnswerSink sink);
}
