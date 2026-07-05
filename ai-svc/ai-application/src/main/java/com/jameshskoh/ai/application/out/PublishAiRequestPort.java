package com.jameshskoh.ai.application.out;

import com.jameshskoh.ai.domain.AiRequest;

public interface PublishAiRequestPort {
    void publish(AiRequest request);
}
