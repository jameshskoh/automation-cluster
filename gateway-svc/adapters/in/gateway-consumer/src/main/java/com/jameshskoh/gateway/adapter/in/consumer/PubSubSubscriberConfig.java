package com.jameshskoh.gateway.adapter.in.consumer;

import com.google.cloud.pubsub.v1.Subscriber;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.jameshskoh.gateway.application.in.AsyncAnswerUseCase;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableConfigurationProperties(GatewayResponsePubSubProperties.class)
public class PubSubSubscriberConfig {

  @Bean(initMethod = "startAsync", destroyMethod = "stopAsync")
  public Subscriber gatewayResponseSubscriber(
      GatewayResponsePubSubProperties properties,
      AsyncAnswerUseCase asyncAnswerUseCase,
      ObjectMapper objectMapper) {
    ProjectSubscriptionName subscriptionName =
        ProjectSubscriptionName.of(properties.projectId(), properties.subscriptionId());
    return Subscriber.newBuilder(
            subscriptionName, new GatewayResponseSubscriber(asyncAnswerUseCase, objectMapper))
        .build();
  }
}
