package com.jameshskoh.weather.messaging.in;

import com.google.cloud.functions.HttpRequest;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Minimal {@link HttpRequest} fake carrying only a raw body — all {@code WeatherFunction} reads. */
final class FakeHttpRequest implements HttpRequest {

  private final byte[] body;

  FakeHttpRequest(String body) {
    this.body = body.getBytes(StandardCharsets.UTF_8);
  }

  @Override
  public String getMethod() {
    return "POST";
  }

  @Override
  public String getUri() {
    return "/";
  }

  @Override
  public String getPath() {
    return "/";
  }

  @Override
  public Optional<String> getQuery() {
    return Optional.empty();
  }

  @Override
  public Map<String, List<String>> getQueryParameters() {
    return Collections.emptyMap();
  }

  @Override
  public Map<String, HttpPart> getParts() {
    return Collections.emptyMap();
  }

  @Override
  public Optional<String> getContentType() {
    return Optional.of("application/json");
  }

  @Override
  public long getContentLength() {
    return body.length;
  }

  @Override
  public Optional<String> getCharacterEncoding() {
    return Optional.of("UTF-8");
  }

  @Override
  public InputStream getInputStream() {
    return new ByteArrayInputStream(body);
  }

  @Override
  public BufferedReader getReader() throws IOException {
    return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
  }

  @Override
  public Map<String, List<String>> getHeaders() {
    return Collections.emptyMap();
  }
}
