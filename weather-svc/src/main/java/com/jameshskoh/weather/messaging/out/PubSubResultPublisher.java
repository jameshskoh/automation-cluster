package com.jameshskoh.weather.messaging.out;

import com.google.cloud.pubsub.v1.Publisher;
import com.google.gson.Gson;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PubsubMessage;
import com.google.pubsub.v1.TopicName;
import com.jameshskoh.weather.config.WeatherConfig;
import com.jameshskoh.weather.domain.ForecastBlocks;
import com.jameshskoh.weather.domain.ResolvedLocation;
import com.jameshskoh.weather.messaging.in.GatewayMessage;
import com.jameshskoh.weather.port.PublishException;
import com.jameshskoh.weather.port.ResultPublisher;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

/**
 * {@link ResultPublisher} over Pub/Sub: publishes both {@code FETCHED} and {@code FAILED} to
 * weather-svc's outbound topic {@code weather-svc-results}, stamping {@code use_case}/{@code stage}
 * in both the Pub/Sub attributes (for subscription filters) and the body. See docs/arch/messaging.md,
 * "Outbound messages".
 *
 * <p>Blocks on the publish future so success or failure (ack vs. nack -> DLQ) is known before the
 * caller returns its HTTP status.
 */
public final class PubSubResultPublisher implements ResultPublisher {

  private static final Gson GSON = new Gson();
  private static final String USE_CASE = "WEATHER";
  private static final String FETCHED_STAGE = "FETCHED";
  private static final String FAILED_STAGE = "FAILED";

  private final Publisher publisher;

  public PubSubResultPublisher(Publisher publisher) {
    this.publisher = publisher;
  }

  public static PubSubResultPublisher forTopic(WeatherConfig config) throws IOException {
    Publisher publisher = Publisher.newBuilder(
            TopicName.of(config.gcpProjectId(), config.weatherResultsTopicId()))
        .build();
    return new PubSubResultPublisher(publisher);
  }

  @Override
  public void publishFetched(String requestId, ResolvedLocation location, ForecastBlocks blocks)
      throws PublishException {
    publish(buildFetchedMessage(requestId, location, blocks));
  }

  @Override
  public void publishFailed(String requestId, String reason) throws PublishException {
    publish(buildFailedMessage(requestId, reason));
  }

  static GatewayMessage buildFetchedMessage(
      String requestId, ResolvedLocation location, ForecastBlocks blocks) {
    String payloadJson = GSON.toJson(FetchedPayload.from(location, blocks));
    String prompt = InterpretationPromptBuilder.build(location);
    return new GatewayMessage(USE_CASE, FETCHED_STAGE, requestId, payloadJson, prompt);
  }

  static GatewayMessage buildFailedMessage(String requestId, String reason) {
    return new GatewayMessage(USE_CASE, FAILED_STAGE, requestId, "", reason);
  }

  private void publish(GatewayMessage message) throws PublishException {
    PubsubMessage pubsubMessage = PubsubMessage.newBuilder()
        .setData(ByteString.copyFromUtf8(GSON.toJson(message)))
        .putAttributes("use_case", message.useCase())
        .putAttributes("stage", message.stage())
        .build();
    try {
      publisher.publish(pubsubMessage).get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new PublishException(
          "Interrupted while publishing requestId=" + message.requestId(), e);
    } catch (ExecutionException e) {
      Throwable cause = e.getCause() != null ? e.getCause() : e;
      throw new PublishException(
          "Failed to publish requestId=" + message.requestId(), cause);
    }
  }
}
