package com.jameshskoh.ai;

import com.jameshskoh.ai.application.PendingAnswerRegistry;
import com.jameshskoh.ai.application.in.AsyncAnswerService;
import com.jameshskoh.ai.application.in.AsyncAnswerUseCase;
import com.jameshskoh.ai.application.in.AsyncQuestionService;
import com.jameshskoh.ai.application.in.AsyncQuestionUseCase;
import com.jameshskoh.ai.application.in.HelloAsyncService;
import com.jameshskoh.ai.application.in.HelloAsyncUseCase;
import com.jameshskoh.ai.application.in.HelloService;
import com.jameshskoh.ai.application.in.HelloUseCase;
import com.jameshskoh.ai.application.out.PublishAiRequestPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UseCaseConfig {

  @Bean
  public HelloUseCase helloUseCase() {
    return new HelloService();
  }

  @Bean
  public HelloAsyncUseCase helloAsyncUseCase() {
    return new HelloAsyncService();
  }

  @Bean
  public AsyncQuestionUseCase asyncQuestionUseCase(
      PublishAiRequestPort publishAiRequestPort, PendingAnswerRegistry pendingAnswerRegistry) {
    return new AsyncQuestionService(publishAiRequestPort, pendingAnswerRegistry);
  }

  @Bean
  public PendingAnswerRegistry pendingAnswerRegistry() {
    return new PendingAnswerRegistry();
  }

  @Bean
  public AsyncAnswerUseCase asyncAnswerUseCase(PendingAnswerRegistry pendingAnswerRegistry) {
    return new AsyncAnswerService(pendingAnswerRegistry);
  }
}
