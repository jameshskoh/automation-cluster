package com.jameshskoh.gateway.application.out;

import com.jameshskoh.gateway.domain.GatewayMessage;

public interface PublishGatewayMessagePort {
    void publish(GatewayMessage message);
}
