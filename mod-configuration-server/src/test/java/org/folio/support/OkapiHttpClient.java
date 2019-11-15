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

  public OkapiHttpClient(Vertx vertx, String tenantId, String userId) {
    this.vertx = vertx;
    this.tenantId = tenantId;
    this.userId = userId;
  }

  public CompletableFuture<Response> post(String url, String jsonContent) {
    HttpClient client = vertx.createHttpClient();

    HttpClientRequest request = client.postAbs(url);
    Buffer requestBuffer = Buffer.buffer(jsonContent);

    final CompletableFuture<Response> postCompleted = new CompletableFuture<>();

    request.exceptionHandler(postCompleted::completeExceptionally);

    request.setTimeout(5000);

    request.handler(response ->
      response.bodyHandler(responseBuffer -> {
        final Response convertedResponse = new Response(
          response.statusCode(),
          responseBuffer.getString(0, responseBuffer.length()));

        System.out.println(String.format("Received response: '%s':'%s'",
          convertedResponse.getStatusCode(), convertedResponse.getBody()));

        postCompleted.complete(convertedResponse);
      }));

    request.putHeader("X-Okapi-Tenant", tenantId);
    request.putHeader("X-Okapi-User-Id", userId);
    request.putHeader("Content-type", "application/json");
    request.putHeader("Accept", "application/json, text/plain");

    request.end(requestBuffer);

    return postCompleted;
  }

  public CompletableFuture<Response> get(String url) {
    HttpClient client = vertx.createHttpClient();

    HttpClientRequest request = client.getAbs(url);

    final CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    request.exceptionHandler(getCompleted::completeExceptionally);

    request.setTimeout(5000);

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
    request.putHeader("X-Okapi-User-Id", userId);
    request.putHeader("Accept", "application/json, text/plain");

    request.end();

    return getCompleted;
  }

  public CompletableFuture<Response> put(String url, String jsonContent) {
    HttpClient client = vertx.createHttpClient();

    HttpClientRequest request = client.putAbs(url);
    Buffer requestBuffer = Buffer.buffer(jsonContent);

    final CompletableFuture<Response> putCompleted = new CompletableFuture<>();

    request.exceptionHandler(putCompleted::completeExceptionally);

    request.setTimeout(5000);

    request.handler(response ->
      response.bodyHandler(responseBuffer -> {
        final Response convertedResponse = new Response(
          response.statusCode(),
          responseBuffer.getString(0, responseBuffer.length()));

        System.out.println(String.format("Received response: '%s':'%s'",
          convertedResponse.getStatusCode(), convertedResponse.getBody()));

        putCompleted.complete(convertedResponse);
      }));

    request.putHeader("X-Okapi-Tenant", tenantId);
    request.putHeader("X-Okapi-User-Id", userId);
    request.putHeader("Content-type", "application/json");
    request.putHeader("Accept", "application/json, text/plain");

    request.end(requestBuffer);

    return putCompleted;
  }
}
