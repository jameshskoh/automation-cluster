package com.jameshskoh.weather.messaging.in;

import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import com.jameshskoh.weather.port.PublishException;
import com.jameshskoh.weather.service.CompositionRoot;
import com.jameshskoh.weather.service.WeatherService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The Cloud Run function entrypoint: an {@link HttpFunction} invoked by weather-svc's Pub/Sub
 * *push* subscription ({@code weather-svc-gateway-requests-sub}, filter {@code WEATHER AND
 * REQUESTED}), not an Eventarc trigger — see docs/architecture.md, "Trigger". Built once per warm
 * container instance, not per request.
 *
 * <p>Ack/nack (docs/arch/messaging.md, "Error -> ack/nack"): an undecodable push envelope or a
 * publish failure returns non-2xx so Pub/Sub redelivers; a successful {@code FETCHED}/{@code FAILED}
 * publish returns 2xx.
 */
public final class WeatherFunction implements HttpFunction {

  private static final Logger log = Logger.getLogger(WeatherFunction.class.getName());

  private final WeatherService weatherService;

  public WeatherFunction() {
    this(CompositionRoot.buildWeatherService());
  }

  /** Package-visible seam for tests to inject a {@link WeatherService} wired to fakes. */
  WeatherFunction(WeatherService weatherService) {
    this.weatherService = weatherService;
  }

  @Override
  public void service(HttpRequest request, HttpResponse response) throws IOException {
    String rawBody = new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

    GatewayMessage message;
    try {
      message = PushEnvelopeParser.parse(rawBody);
    } catch (RuntimeException e) {
      log.log(Level.SEVERE, "Failed to parse push envelope; returning 500 for redelivery.", e);
      response.setStatusCode(500);
      return;
    }

    try {
      weatherService.process(message);
      response.setStatusCode(200);
    } catch (PublishException e) {
      log.log(Level.SEVERE, "Failed to publish result for requestId=" + message.requestId()
          + "; returning 500 for redelivery.", e);
      response.setStatusCode(500);
    }
  }
}
