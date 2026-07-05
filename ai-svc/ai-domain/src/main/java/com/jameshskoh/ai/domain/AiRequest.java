package com.jameshskoh.ai.domain;

public record AiRequest(String uuid, String question) {

  public AiRequest {
    if (uuid == null || uuid.isBlank()) {
      throw new IllegalArgumentException("uuid must not be null or blank");
    }
    if (question == null || question.isBlank()) {
      throw new IllegalArgumentException("question must not be null or blank");
    }
  }
}
