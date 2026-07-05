package com.jameshskoh.ai.application.in;

import com.jameshskoh.ai.application.PendingAnswerRegistry;
import com.jameshskoh.ai.domain.AiResponse;

public class AsyncAnswerService implements AsyncAnswerUseCase {

  private final PendingAnswerRegistry pendingAnswerRegistry;

  public AsyncAnswerService(PendingAnswerRegistry pendingAnswerRegistry) {
    this.pendingAnswerRegistry = pendingAnswerRegistry;
  }

  @Override
  public void answer(AiResponse response) {
    pendingAnswerRegistry.complete(response.uuid(), response);
  }
}
