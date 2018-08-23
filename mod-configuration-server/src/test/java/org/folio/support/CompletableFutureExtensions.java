package org.folio.support;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class CompletableFutureExtensions {
  private CompletableFutureExtensions() { }

  public static <T> CompletableFuture<Void> allOf(
    List<CompletableFuture<T>> allFutures) {

    return CompletableFuture.allOf(allFutures.toArray(new CompletableFuture<?>[] { }));
  }
}
