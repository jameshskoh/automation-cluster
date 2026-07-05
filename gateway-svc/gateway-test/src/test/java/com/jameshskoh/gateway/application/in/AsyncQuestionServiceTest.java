package com.jameshskoh.gateway.application.in;

import static org.assertj.core.api.Assertions.assertThat;

import com.jameshskoh.gateway.application.PendingAnswerRegistry;
import com.jameshskoh.gateway.application.out.PublishGatewayMessagePort;
import com.jameshskoh.gateway.domain.GatewayMessage;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

class AsyncQuestionServiceTest {

  @Test
  void askPublishesRequestAndRegistersCallerSinkUnderReturnedRequestId() {
    AtomicReference<GatewayMessage> published = new AtomicReference<>();
    PublishGatewayMessagePort publishPort = published::set;
    PendingAnswerRegistry registry = new PendingAnswerRegistry();
    AsyncQuestionService service = new AsyncQuestionService(publishPort, registry);

    AtomicReference<GatewayMessage> delivered = new AtomicReference<>();
    String requestId = service.ask(new GatewayRequestContent("ping", ""), delivered::set);

    // The request was published with the question under the returned requestId.
    assertThat(requestId).isNotBlank();
    assertThat(published.get().requestId()).isEqualTo(requestId);
    assertThat(published.get().metadata()).isEqualTo("ping");
    assertThat(published.get().payload()).isEmpty();
    assertThat(published.get().useCase()).isEqualTo("QA");
    assertThat(published.get().stage()).isEqualTo("ASKED");

    // The caller-supplied sink is registered under that requestId: completing it delivers the
    // answer.
    GatewayMessage response = new GatewayMessage("QA", "ANSWERED", requestId, "", "pong");
    registry.complete(requestId, response);
    assertThat(delivered.get()).isEqualTo(response);
  }
}
