package com.jameshskoh.gateway.application.in;

import com.jameshskoh.gateway.application.PendingAnswerRegistry;
import com.jameshskoh.gateway.domain.GatewayMessage;

public class AsyncAnswerService implements AsyncAnswerUseCase {

  private final PendingAnswerRegistry pendingAnswerRegistry;

  public AsyncAnswerService(PendingAnswerRegistry pendingAnswerRegistry) {
    this.pendingAnswerRegistry = pendingAnswerRegistry;
  }

  @Override
  public void answer(GatewayMessage message) {
    pendingAnswerRegistry.complete(message.requestId(), message);
  }
}
