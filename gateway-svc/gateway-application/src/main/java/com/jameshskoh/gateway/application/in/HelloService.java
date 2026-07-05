package com.jameshskoh.gateway.application.in;

public class HelloService implements HelloUseCase {
  @Override
  public String sayHello() {
    return "Hello, world!";
  }
}
