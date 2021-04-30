package org.folio.support;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;

import java.util.concurrent.CompletableFuture;

public class OkapiHttpClient {
  private final String tenantId;
  private final String userId;
  private final WebClient client;

  public OkapiHttpClient(Vertx vertx, String tenantId, String userId) {
    this.tenantId = tenantId;
    this.userId = userId;
    WebClientOptions options = new WebClientOptions();
    options.setConnectTimeout(5000);
    options.setIdleTimeout(5000);
    this.client = WebClient.create(vertx);
  }

  public CompletableFuture<Response> post(String url, String jsonContent) {
    HttpRequest<Buffer> request = client.postAbs(url);

    final CompletableFuture<Response> postCompleted = new CompletableFuture<>();

    request.putHeader("X-Okapi-Tenant", tenantId);
    request.putHeader("X-Okapi-User-Id", userId);
    request.putHeader("Content-type", "application/json");
    request.putHeader("Accept", "application/json, text/plain");

    request.sendBuffer(Buffer.buffer(jsonContent))
        .onFailure(postCompleted::completeExceptionally)
        .onSuccess(res -> postCompleted.complete(new Response(res.statusCode(), res.bodyAsString())));
    return postCompleted;
  }


  public CompletableFuture<Response> get(String url, String tenantId) {
    HttpRequest<Buffer> request = client.getAbs(url);

    final CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    request.putHeader("X-Okapi-User-Id", userId);
    request.putHeader("Accept", "application/json, text/plain");
    if (tenantId != null) {
      request.putHeader("X-Okapi-Tenant", tenantId);
    }

    request.send()
        .onFailure(getCompleted::completeExceptionally)
        .onSuccess(res -> getCompleted.complete(new Response(res.statusCode(), res.bodyAsString())));

    return getCompleted;
  }

  public CompletableFuture<Response> get(String url) {
    return get(url, this.tenantId);
  }

  public CompletableFuture<Response> put(String url, String jsonContent) {
    HttpRequest<Buffer> request = client.putAbs(url);

    final CompletableFuture<Response> putCompleted = new CompletableFuture<>();

    request.putHeader("X-Okapi-Tenant", tenantId);
    request.putHeader("X-Okapi-User-Id", userId);
    request.putHeader("Content-type", "application/json");
    request.putHeader("Accept", "application/json, text/plain");

    request.sendBuffer(Buffer.buffer(jsonContent))
        .onFailure(putCompleted::completeExceptionally)
        .onSuccess(res -> putCompleted.complete(new Response(res.statusCode(), res.bodyAsString())));
    return putCompleted;
  }
}
