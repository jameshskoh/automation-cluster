package com.jameshskoh.ai.domain;

public record AiResponse(String uuid, String answer) {

  public AiResponse {
    if (uuid == null || uuid.isBlank()) {
      throw new IllegalArgumentException("uuid must not be null or blank");
    }
    if (answer == null) {
      throw new IllegalArgumentException("answer must not be null");
    }
  }
}
