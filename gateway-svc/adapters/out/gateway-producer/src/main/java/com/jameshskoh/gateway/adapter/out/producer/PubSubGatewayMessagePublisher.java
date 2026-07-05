package com.jameshskoh.gateway.adapter.out.producer;

import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutureCallback;
import com.google.api.core.ApiFutures;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PubsubMessage;
import com.jameshskoh.gateway.application.out.PublishGatewayMessagePort;
import com.jameshskoh.gateway.domain.GatewayMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class PubSubGatewayMessagePublisher implements PublishGatewayMessagePort {

  private static final Logger log = LoggerFactory.getLogger(PubSubGatewayMessagePublisher.class);

  private final Publisher publisher;
  private final ObjectMapper objectMapper;

  public PubSubGatewayMessagePublisher(Publisher publisher, ObjectMapper objectMapper) {
    this.publisher = publisher;
    this.objectMapper = objectMapper;
  }

  @Override
  public void publish(GatewayMessage message) {
    // use_case/stage are set as Pub/Sub message attributes (not just body fields) because the
    // subscription filters in provision-pubsub.sh select on attributes.use_case/attributes.stage.
    // A message missing these attributes matches no filter and is silently dropped by Pub/Sub.
    PubsubMessage pubsubMessage = PubsubMessage.newBuilder()
        .setData(ByteString.copyFrom(toJsonBytes(message)))
        .putAttributes("use_case", message.useCase())
        .putAttributes("stage", message.stage())
        .build();
    // Publish without blocking the caller (the servlet thread on the /qa/async path). The publish
    // result is handled asynchronously; a failure is logged, and the caller's long-poll will time
    // out since no answer will ever arrive for this requestId.
    ApiFuture<String> future = publisher.publish(pubsubMessage);
    ApiFutures.addCallback(future, new ApiFutureCallback<>() {
      @Override
      public void onFailure(Throwable t) {
        log.error("Failed to publish GatewayMessage requestId={}", message.requestId(), t);
      }

      @Override
      public void onSuccess(String messageId) {
        log.debug(
            "Published GatewayMessage requestId={} as messageId={}", message.requestId(),
            messageId);
      }
    }, MoreExecutors.directExecutor());
  }

  private byte[] toJsonBytes(GatewayMessage message) {
    try {
      return objectMapper.writeValueAsBytes(GatewayMessageWire.from(message));
    } catch (JacksonException e) {
      throw new IllegalArgumentException("Failed to serialize GatewayMessage", e);
    }
  }
}
