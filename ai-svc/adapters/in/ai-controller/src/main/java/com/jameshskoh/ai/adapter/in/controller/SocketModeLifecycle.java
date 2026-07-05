package com.jameshskoh.ai.adapter.in.controller;

import com.slack.api.bolt.jakarta_socket_mode.SocketModeApp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

@Component
public class SocketModeLifecycle implements SmartLifecycle {

  private static final Logger log = LoggerFactory.getLogger(SocketModeLifecycle.class);

  private final SocketModeApp socketModeApp;
  private volatile boolean running = false;

  public SocketModeLifecycle(SocketModeApp socketModeApp) {
    this.socketModeApp = socketModeApp;
  }

  @Override
  public void start() {
    try {
      socketModeApp.startAsync();
      running = true;
    } catch (Exception e) {
      throw new IllegalStateException("Failed to start Slack Socket Mode connection", e);
    }
  }

  @Override
  public void stop() {
    try {
      socketModeApp.stop();
    } catch (Exception e) {
      log.error("Error stopping Slack Socket Mode connection", e);
    } finally {
      running = false;
    }
  }

  @Override
  public boolean isRunning() {
    return running;
  }
}
