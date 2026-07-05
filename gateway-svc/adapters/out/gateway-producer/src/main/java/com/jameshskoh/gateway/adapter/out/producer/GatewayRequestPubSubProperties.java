package com.jameshskoh.gateway.adapter.out.producer;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gcp.pubsub.gateway-requests")
public record GatewayRequestPubSubProperties(String projectId, String topicId) {
}
