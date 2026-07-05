package com.jameshskoh.gateway.application.in;

import com.jameshskoh.gateway.domain.GatewayMessage;

public interface AsyncAnswerUseCase {
  void answer(GatewayMessage message);
}
