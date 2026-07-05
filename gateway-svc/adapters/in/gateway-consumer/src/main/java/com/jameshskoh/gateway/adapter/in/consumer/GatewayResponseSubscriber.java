package com.jameshskoh.gateway.adapter.in.consumer;

import com.google.cloud.pubsub.v1.AckReplyConsumer;
import com.google.cloud.pubsub.v1.MessageReceiver;
import com.google.pubsub.v1.PubsubMessage;
import com.jameshskoh.gateway.application.in.AsyncAnswerUseCase;
import com.jameshskoh.gateway.domain.GatewayMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tools.jackson.databind.ObjectMapper;

public class GatewayResponseSubscriber implements MessageReceiver {

  private static final Logger log = LoggerFactory.getLogger(GatewayResponseSubscriber.class);

  private final AsyncAnswerUseCase asyncAnswerUseCase;
  private final ObjectMapper objectMapper;

  public GatewayResponseSubscriber(
      AsyncAnswerUseCase asyncAnswerUseCase, ObjectMapper objectMapper) {
    this.asyncAnswerUseCase = asyncAnswerUseCase;
    this.objectMapper = objectMapper;
  }

  @Override
  public void receiveMessage(PubsubMessage message, AckReplyConsumer consumer) {
    try {
      GatewayMessageWire wire = objectMapper.readValue(
          message.getData().toByteArray(), GatewayMessageWire.class);
      GatewayMessage gatewayMessage = wire.toDomain();
      asyncAnswerUseCase.answer(gatewayMessage);
      consumer.ack();
    } catch (Exception e) {
      // TODO: There is no dead-letter topic yet. Until one exists, a poison message (bad JSON,
      // failed handling) would redeliver forever if nack'd, so we log its content and ack it to
      // drop it. Replace this with a dead-letter subscription and nack once available.
      log.error("Failed to handle GatewayMessage, dropping. payload={}",
          message.getData().toStringUtf8(), e);
      consumer.ack();
    }
  }
}
