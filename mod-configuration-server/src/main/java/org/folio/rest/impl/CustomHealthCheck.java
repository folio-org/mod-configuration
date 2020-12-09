package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.Map;

import javax.ws.rs.core.Response;

import org.folio.rest.tools.utils.OutStream;

/**
 * Pass through AdminAPI
 */
public class CustomHealthCheck extends AdminAPI {
  private static final Logger log = LoggerFactory.getLogger(CustomHealthCheck.class);

  @Override
  public void getAdminHealth(Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext)  {

    super.getAdminHealth(okapiHeaders,  res -> {

      OutStream stream = new OutStream();
      stream.setData("OK");

      log.info(" --- this is an over ride of the health API by the config module "+res.result().getStatus());
      asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetAdminHealthResponse.respond200WithAnyAny(stream)));
    }, vertxContext);
  }
}

