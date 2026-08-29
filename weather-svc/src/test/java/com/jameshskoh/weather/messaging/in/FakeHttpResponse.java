package com.jameshskoh.weather.messaging.in;

import com.google.cloud.functions.HttpResponse;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Minimal {@link HttpResponse} fake capturing just the status code {@code WeatherFunction} sets. */
final class FakeHttpResponse implements HttpResponse {

  private int statusCode = 200;
  private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

  int statusCode() {
    return statusCode;
  }

  @Override
  public void setStatusCode(int code) {
    this.statusCode = code;
  }

  @Override
  public void setStatusCode(int code, String message) {
    this.statusCode = code;
  }

  @Override
  public void setContentType(String contentType) {}

  @Override
  public Optional<String> getContentType() {
    return Optional.empty();
  }

  @Override
  public void appendHeader(String header, String value) {}

  @Override
  public Map<String, List<String>> getHeaders() {
    return Collections.emptyMap();
  }

  @Override
  public OutputStream getOutputStream() {
    return outputStream;
  }

  @Override
  public BufferedWriter getWriter() throws IOException {
    return new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));
  }
}
