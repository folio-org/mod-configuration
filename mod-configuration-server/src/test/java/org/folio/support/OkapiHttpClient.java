package org.folio.support;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;

import java.util.concurrent.CompletableFuture;

public class OkapiHttpClient {
  private final Vertx vertx;
  private final String tenantId;
  private final String userId;
  private final String token;

  public OkapiHttpClient(Vertx vertx, String tenantId, String userId, String token) {
    this.vertx = vertx;
    this.tenantId = tenantId;
    this.userId = userId;
    this.token = token;
  }

  public CompletableFuture<Response> post(String url, String jsonContent) {
    HttpClient client = vertx.createHttpClient();

    HttpClientRequest request = client.postAbs(url);
    Buffer requestBuffer = Buffer.buffer(jsonContent);

    final CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    request.exceptionHandler(getCompleted::completeExceptionally);

    request.handler(response ->
      response.bodyHandler(responseBuffer -> {
        final Response convertedResponse = new Response(
          response.statusCode(),
          responseBuffer.getString(0, responseBuffer.length()));

        System.out.println(String.format("Received response: '%s':'%s'",
          convertedResponse.getStatusCode(), convertedResponse.getBody()));

        getCompleted.complete(convertedResponse);
      }));

    request.putHeader("X-Okapi-Tenant", tenantId);
    request.putHeader("X-Okapi-Token", token);
    request.putHeader("X-Okapi-User-Id", userId);
    request.putHeader("Content-type", "application/json");
    request.putHeader("Accept", "application/json, text/plain");

    request.end(requestBuffer);

    return getCompleted;
  }

  public CompletableFuture<Response> get(String url) {
    HttpClient client = vertx.createHttpClient();

    HttpClientRequest request = client.getAbs(url);

    final CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    request.exceptionHandler(getCompleted::completeExceptionally);

    request.handler(response ->
      response.bodyHandler(buffer -> getCompleted.complete(
        new Response(response.statusCode(),
          buffer.getString(0, buffer.length())))));

    request.putHeader("X-Okapi-Tenant", tenantId);
    request.putHeader("X-Okapi-Token", token);
    request.putHeader("X-Okapi-User-Id", userId);
    request.putHeader("Accept", "application/json, text/plain");

    request.end();

    return getCompleted;
  }
}
