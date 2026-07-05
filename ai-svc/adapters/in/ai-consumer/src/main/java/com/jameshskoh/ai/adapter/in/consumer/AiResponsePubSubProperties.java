package com.jameshskoh.ai.adapter.in.consumer;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gcp.pubsub.claude-automator-responses")
public record AiResponsePubSubProperties(String projectId, String subscriptionId) {
}
