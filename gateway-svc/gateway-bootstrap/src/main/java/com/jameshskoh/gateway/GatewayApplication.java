package com.jameshskoh.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class GatewayApplication {

  static void main(String[] args) {
    SpringApplication.run(GatewayApplication.class, args);
  }
}
