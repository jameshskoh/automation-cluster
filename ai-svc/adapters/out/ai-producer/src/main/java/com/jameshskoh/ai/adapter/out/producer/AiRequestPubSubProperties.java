package com.jameshskoh.ai.adapter.out.producer;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gcp.pubsub.gateway-requests")
public record AiRequestPubSubProperties(String projectId, String topicId) {
}
