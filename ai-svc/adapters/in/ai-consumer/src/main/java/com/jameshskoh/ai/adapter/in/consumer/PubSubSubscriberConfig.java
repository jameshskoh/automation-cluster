package com.jameshskoh.ai.adapter.in.consumer;

import com.google.cloud.pubsub.v1.Subscriber;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.jameshskoh.ai.application.in.AsyncAnswerUseCase;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableConfigurationProperties(AiResponsePubSubProperties.class)
public class PubSubSubscriberConfig {

  @Bean(initMethod = "startAsync", destroyMethod = "stopAsync")
  public Subscriber aiResponseSubscriber(
      AiResponsePubSubProperties properties,
      AsyncAnswerUseCase asyncAnswerUseCase,
      ObjectMapper objectMapper) {
    ProjectSubscriptionName subscriptionName =
        ProjectSubscriptionName.of(properties.projectId(), properties.subscriptionId());
    return Subscriber.newBuilder(
            subscriptionName, new AiResponseSubscriber(asyncAnswerUseCase, objectMapper))
        .build();
  }
}
