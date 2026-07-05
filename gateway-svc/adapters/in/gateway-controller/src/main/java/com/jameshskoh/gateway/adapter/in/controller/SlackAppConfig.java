package com.jameshskoh.gateway.adapter.in.controller;

import com.jameshskoh.gateway.application.AnswerSink;
import com.jameshskoh.gateway.application.in.AsyncQuestionUseCase;
import com.jameshskoh.gateway.application.in.GatewayRequestContent;
import com.jameshskoh.gateway.application.in.HelloAsyncUseCase;
import com.jameshskoh.gateway.application.in.HelloUseCase;
import com.slack.api.bolt.App;
import com.slack.api.bolt.AppConfig;
import com.slack.api.bolt.jakarta_socket_mode.SocketModeApp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@Configuration
public class SlackAppConfig {

  private static final Logger log = LoggerFactory.getLogger(SlackAppConfig.class);

  @Bean
  public App slackApp(
      @Value("${slack.bot-token}") String botToken,
      HelloUseCase helloUseCase,
      HelloAsyncUseCase helloAsyncUseCase,
      AsyncQuestionUseCase asyncQuestionUseCase) {

    AppConfig config = AppConfig.builder()
        .singleTeamBotToken(botToken)
        .build();
    App app = new App(config);

    app.command("/hello", (req, ctx) ->
        ctx.ack(helloUseCase.sayHello()));

    app.command("/ask", (req, ctx) -> {
      String question = req.getPayload().getText();
      // Publish the question and register a sink that posts the answer back to the channel when the
      // response arrives (delivered later by the subscriber thread). The sink's response_url is
      // valid for ~30 min / 5 uses; a never-arriving answer is bounded by the TTL sweeper, whose
      // onExpire posts a timeout notice.
      asyncQuestionUseCase.ask(new GatewayRequestContent(question, ""), new AnswerSink() {
        @Override
        public void deliver(com.jameshskoh.gateway.domain.GatewayMessage message) {
          respondSafely(ctx, message.metadata() + "\n---\n" + message.payload());
        }

        @Override
        public void onExpire() {
          respondSafely(ctx, "Sorry, that took too long — no answer came back in time.");
        }
      });
      return ctx.ack("On it — answer coming shortly.");
    });

    app.command("/hello-async", (req, ctx) -> {
      app.executorService().submit(() -> {
        try {
          String result = helloAsyncUseCase.sayHelloSlowly();
          ctx.respond(result);
        } catch (Exception e) {
          log.error("Failed to answer /hello-async command", e);
          respondSafely(ctx, "Sorry, something went wrong.");
        }
      });
      return ctx.ack("On it — result coming in ~5 seconds.");
    });

    return app;
  }

  private static void respondSafely(
      com.slack.api.bolt.context.builtin.SlashCommandContext ctx, String message) {
    try {
      ctx.respond(message);
    } catch (Exception e) {
      log.error("Failed to send error response to Slack", e);
    }
  }

  @Bean
  public SocketModeApp socketModeApp(
      App slackApp,
      @Value("${slack.app-token}") String appToken) throws IOException {
    return new SocketModeApp(appToken, slackApp);
  }
}
