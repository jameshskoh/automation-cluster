package com.jameshskoh.ai.application.in;

import static org.assertj.core.api.Assertions.assertThat;

import com.jameshskoh.ai.application.PendingAnswerRegistry;
import com.jameshskoh.ai.application.out.PublishAiRequestPort;
import com.jameshskoh.ai.domain.AiRequest;
import com.jameshskoh.ai.domain.AiResponse;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

class AsyncQuestionServiceTest {

  @Test
  void askPublishesRequestAndRegistersCallerSinkUnderReturnedUuid() {
    AtomicReference<AiRequest> published = new AtomicReference<>();
    PublishAiRequestPort publishPort = published::set;
    PendingAnswerRegistry registry = new PendingAnswerRegistry();
    AsyncQuestionService service = new AsyncQuestionService(publishPort, registry);

    AtomicReference<AiResponse> delivered = new AtomicReference<>();
    String uuid = service.ask("ping", delivered::set);

    // The request was published with the question under the returned uuid.
    assertThat(uuid).isNotBlank();
    assertThat(published.get().uuid()).isEqualTo(uuid);
    assertThat(published.get().question()).isEqualTo("ping");

    // The caller-supplied sink is registered under that uuid: completing it delivers the answer.
    AiResponse response = new AiResponse(uuid, "pong");
    registry.complete(uuid, response);
    assertThat(delivered.get()).isEqualTo(response);
  }
}
