package com.jameshskoh.gateway.application.in;

import com.jameshskoh.gateway.application.AnswerSink;
import com.jameshskoh.gateway.application.PendingAnswerRegistry;
import com.jameshskoh.gateway.application.out.PublishGatewayMessagePort;
import com.jameshskoh.gateway.domain.GatewayMessage;

import java.util.UUID;

public class AsyncQuestionService implements AsyncQuestionUseCase {

  private static final String QA_USE_CASE = "QA";
  private static final String ASKED_STAGE = "ASKED";

  private final PublishGatewayMessagePort publishGatewayMessagePort;
  private final PendingAnswerRegistry pendingAnswerRegistry;

  public AsyncQuestionService(
      PublishGatewayMessagePort publishGatewayMessagePort,
      PendingAnswerRegistry pendingAnswerRegistry) {
    this.publishGatewayMessagePort = publishGatewayMessagePort;
    this.pendingAnswerRegistry = pendingAnswerRegistry;
  }

  @Override
  public String ask(GatewayRequestContent content, AnswerSink sink) {
    String requestId = UUID.randomUUID().toString();
    // Register before publishing so a fast round-trip cannot complete the answer before the
    // sink exists (otherwise the response would be dropped and the caller would never hear back).
    pendingAnswerRegistry.register(requestId, sink);
    publishGatewayMessagePort.publish(
        new GatewayMessage(
            QA_USE_CASE, ASKED_STAGE, requestId, content.payload(), content.metadata()));
    return requestId;
  }
}
