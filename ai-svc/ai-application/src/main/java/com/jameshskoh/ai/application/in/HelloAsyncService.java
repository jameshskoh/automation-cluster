package com.jameshskoh.ai.application.in;

public class HelloAsyncService implements HelloAsyncUseCase {
    @Override
    public String sayHelloSlowly() {
        try {
            Thread.sleep(5_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return "Hello, world! (async)";
    }
}
