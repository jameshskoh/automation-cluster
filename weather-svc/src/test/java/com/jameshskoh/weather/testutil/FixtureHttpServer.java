package com.jameshskoh.weather.testutil;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * A minimal local HTTP server (JDK-only, no extra test dependency) that always answers with a
 * fixed status + body, capturing the last request path+query for assertions. Stands in for the
 * open-meteo APIs in the client tests.
 */
public final class FixtureHttpServer implements AutoCloseable {

  private final HttpServer server;
  private volatile String lastRequestUri;

  private FixtureHttpServer(HttpServer server) {
    this.server = server;
  }

  public static FixtureHttpServer respondingWith(int statusCode, String body) {
    try {
      HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
      FixtureHttpServer fixture = new FixtureHttpServer(server);
      server.createContext("/", exchange -> {
        fixture.lastRequestUri = exchange.getRequestURI().toString();
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (var os = exchange.getResponseBody()) {
          os.write(bytes);
        }
      });
      server.start();
      return fixture;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public String baseUrl() {
    return "http://localhost:" + server.getAddress().getPort();
  }

  public String lastRequestUri() {
    return lastRequestUri;
  }

  @Override
  public void close() {
    server.stop(0);
  }
}
