package com.jameshskoh.ai.adapter.out.producer;

import com.google.cloud.pubsub.v1.Publisher;
import com.google.pubsub.v1.TopicName;

import java.io.IOException;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AiRequestPubSubProperties.class)
public class PubSubPublisherConfig {

  @Bean(destroyMethod = "shutdown")
  public Publisher aiRequestPublisher(AiRequestPubSubProperties properties) throws IOException {
    TopicName topicName = TopicName.of(properties.projectId(), properties.topicId());
    return Publisher.newBuilder(topicName).build();
  }
}
