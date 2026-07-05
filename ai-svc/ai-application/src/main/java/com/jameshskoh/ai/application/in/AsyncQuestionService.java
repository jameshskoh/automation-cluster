package com.jameshskoh.ai.application.in;

import com.jameshskoh.ai.application.AnswerSink;
import com.jameshskoh.ai.application.PendingAnswerRegistry;
import com.jameshskoh.ai.application.out.PublishAiRequestPort;
import com.jameshskoh.ai.domain.AiRequest;

import java.util.UUID;

public class AsyncQuestionService implements AsyncQuestionUseCase {

  private final PublishAiRequestPort publishAiRequestPort;
  private final PendingAnswerRegistry pendingAnswerRegistry;

  public AsyncQuestionService(
      PublishAiRequestPort publishAiRequestPort, PendingAnswerRegistry pendingAnswerRegistry) {
    this.publishAiRequestPort = publishAiRequestPort;
    this.pendingAnswerRegistry = pendingAnswerRegistry;
  }

  @Override
  public String ask(String question, AnswerSink sink) {
    String uuid = UUID.randomUUID().toString();
    // Register before publishing so a fast round-trip cannot complete the answer before the
    // sink exists (otherwise the response would be dropped and the caller would never hear back).
    pendingAnswerRegistry.register(uuid, sink);
    publishAiRequestPort.publish(new AiRequest(uuid, question));
    return uuid;
  }
}
