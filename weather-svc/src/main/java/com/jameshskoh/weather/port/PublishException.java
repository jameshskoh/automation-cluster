package com.jameshskoh.weather.port;

/**
 * weather-svc genuinely could not publish its outbound {@code FETCHED}/{@code FAILED} message
 * (e.g. a schema-violation publish failure) — signalled so the inbound message is redelivered and
 * ultimately dead-lettered. See docs/arch/messaging.md, "Error -> ack/nack".
 */
public class PublishException extends Exception {

  public PublishException(String message) {
    super(message);
  }

  public PublishException(String message, Throwable cause) {
    super(message, cause);
  }
}
