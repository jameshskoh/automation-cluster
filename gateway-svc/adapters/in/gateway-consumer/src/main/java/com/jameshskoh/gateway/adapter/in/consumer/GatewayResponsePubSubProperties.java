package com.jameshskoh.gateway.adapter.in.consumer;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gcp.pubsub.claude-automator-responses")
public record GatewayResponsePubSubProperties(String projectId, String subscriptionId) {
}
