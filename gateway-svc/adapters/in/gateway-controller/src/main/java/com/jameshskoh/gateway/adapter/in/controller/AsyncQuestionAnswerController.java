package com.jameshskoh.gateway.adapter.in.controller;

import com.jameshskoh.gateway.application.PendingAnswerRegistry;
import com.jameshskoh.gateway.application.in.AsyncQuestionUseCase;
import com.jameshskoh.gateway.application.in.GatewayRequestContent;
import com.jameshskoh.gateway.domain.GatewayMessage;

import java.util.concurrent.CompletableFuture;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;

@RestController
@RequestMapping("/qa/async")
public class AsyncQuestionAnswerController {

  private final AsyncQuestionUseCase asyncQuestionUseCase;
  private final PendingAnswerRegistry pendingAnswerRegistry;
  private final long timeoutMillis;

  public AsyncQuestionAnswerController(
      AsyncQuestionUseCase asyncQuestionUseCase,
      PendingAnswerRegistry pendingAnswerRegistry,
      @Value("${qa.async.timeout-millis}") long timeoutMillis) {
    this.asyncQuestionUseCase = asyncQuestionUseCase;
    this.pendingAnswerRegistry = pendingAnswerRegistry;
    this.timeoutMillis = timeoutMillis;
  }

  @PostMapping
  public DeferredResult<ResponseEntity<GatewayMessage>> ask(@RequestBody QuestionRequest request) {
    DeferredResult<ResponseEntity<GatewayMessage>> result = new DeferredResult<>(timeoutMillis);

    // The long-poll delivery mechanism is a future the servlet container completes; wrap it as an
    // AnswerSink so the generic async-ask primitive stays framework-neutral.
    CompletableFuture<GatewayMessage> future = new CompletableFuture<>();
    String requestId = asyncQuestionUseCase.ask(
        new GatewayRequestContent(request.question(), ""), future::complete);

    future.whenComplete((message, throwable) -> {
      if (throwable == null) {
        result.setResult(ResponseEntity.ok(message));
      } else {
        result.setErrorResult(
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
      }
    });

    result.onTimeout(() ->
        result.setResult(ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).build()));
    result.onCompletion(() -> pendingAnswerRegistry.evict(requestId));

    return result;
  }

  public record QuestionRequest(String question) {
  }
}
