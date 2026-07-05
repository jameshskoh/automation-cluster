package com.jameshskoh.ai.adapter.out.producer;

import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutureCallback;
import com.google.api.core.ApiFutures;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PubsubMessage;
import com.jameshskoh.ai.application.out.PublishAiRequestPort;
import com.jameshskoh.ai.domain.AiRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class PubSubAiRequestPublisher implements PublishAiRequestPort {

  private static final Logger log = LoggerFactory.getLogger(PubSubAiRequestPublisher.class);

  private final Publisher publisher;
  private final ObjectMapper objectMapper;

  public PubSubAiRequestPublisher(Publisher publisher, ObjectMapper objectMapper) {
    this.publisher = publisher;
    this.objectMapper = objectMapper;
  }

  @Override
  public void publish(AiRequest request) {
    PubsubMessage message = PubsubMessage.newBuilder()
        .setData(ByteString.copyFrom(toJsonBytes(request)))
        .build();
    // Publish without blocking the caller (the servlet thread on the /qa/async path). The publish
    // result is handled asynchronously; a failure is logged, and the caller's long-poll will time
    // out since no answer will ever arrive for this uuid.
    ApiFuture<String> future = publisher.publish(message);
    ApiFutures.addCallback(future, new ApiFutureCallback<>() {
      @Override
      public void onFailure(Throwable t) {
        log.error("Failed to publish AiRequest uuid={}", request.uuid(), t);
      }

      @Override
      public void onSuccess(String messageId) {
        log.debug("Published AiRequest uuid={} as messageId={}", request.uuid(), messageId);
      }
    }, MoreExecutors.directExecutor());
  }

  private byte[] toJsonBytes(AiRequest request) {
    try {
      return objectMapper.writeValueAsBytes(request);
    } catch (JacksonException e) {
      throw new IllegalArgumentException("Failed to serialize AiRequest", e);
    }
  }
}
