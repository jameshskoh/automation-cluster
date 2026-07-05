package com.jameshskoh.gateway;

import com.jameshskoh.gateway.application.PendingAnswerRegistry;
import com.jameshskoh.gateway.application.in.AsyncAnswerService;
import com.jameshskoh.gateway.application.in.AsyncAnswerUseCase;
import com.jameshskoh.gateway.application.in.AsyncQuestionService;
import com.jameshskoh.gateway.application.in.AsyncQuestionUseCase;
import com.jameshskoh.gateway.application.in.HelloAsyncService;
import com.jameshskoh.gateway.application.in.HelloAsyncUseCase;
import com.jameshskoh.gateway.application.in.HelloService;
import com.jameshskoh.gateway.application.in.HelloUseCase;
import com.jameshskoh.gateway.application.out.PublishGatewayMessagePort;
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
      PublishGatewayMessagePort publishGatewayMessagePort,
      PendingAnswerRegistry pendingAnswerRegistry) {
    return new AsyncQuestionService(publishGatewayMessagePort, pendingAnswerRegistry);
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
