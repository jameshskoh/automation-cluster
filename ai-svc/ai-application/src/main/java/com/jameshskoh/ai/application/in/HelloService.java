package com.jameshskoh.ai.application.in;

public class HelloService implements HelloUseCase {
  @Override
  public String sayHello() {
    return "Hello, world!";
  }
}
