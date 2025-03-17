package com.example;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

@MicronautTest
public class R2dbcAndMicrometerTest {
  @Inject
  @Client("/")
  HttpClient client;

  @Test
  void testAnnotation() {
    var response = client.toBlocking().exchange(HttpRequest.GET("/hello"), String.class);
	assertEquals(HttpStatus.OK, response.getStatus());
	assertEquals("[hello]", response.getBody().orElseThrow());
  }

  @Test
  void testManual() {
    var response = client.toBlocking().exchange(HttpRequest.GET("/helloManual"), String.class);
	assertEquals(HttpStatus.OK, response.getStatus());
	assertEquals("[hello]", response.getBody().orElseThrow());
  }
}
