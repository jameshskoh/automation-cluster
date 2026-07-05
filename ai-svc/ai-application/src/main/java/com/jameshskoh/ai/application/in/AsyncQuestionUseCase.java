package com.jameshskoh.ai.application.in;

import com.jameshskoh.ai.application.AnswerSink;

public interface AsyncQuestionUseCase {

  /**
   * Registers {@code sink} against a freshly generated uuid, then publishes the question. The sink
   * receives the answer whenever it arrives (delivered via the response subscriber). Registration
   * happens before publishing so a fast round-trip cannot complete the answer before the sink
   * exists. Returns the uuid correlating the request with its eventual answer.
   *
   * <p>The sink is caller-supplied so this one primitive serves every inbound adapter: the HTTP
   * long-poll wraps a future, the Slack command posts back to its channel. Nothing here is tied to
   * any particular delivery channel.
   */
  String ask(String question, AnswerSink sink);
}
