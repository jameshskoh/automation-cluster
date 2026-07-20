package com.jameshskoh.weather.port;

/**
 * A transient failure calling either open-meteo API: a 5xx response, a network error, or a
 * timeout. v1 does no in-process retry (see docs/architecture.md, "Error posture").
 */
public class OpenMeteoException extends Exception {

  public OpenMeteoException(String message) {
    super(message);
  }

  public OpenMeteoException(String message, Throwable cause) {
    super(message, cause);
  }
}
