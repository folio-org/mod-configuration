package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;

import java.util.Map;

import javax.ws.rs.core.Response;

import org.folio.rest.tools.utils.OutStream;

/**
 * @author shale
 *
 */
public class CustomHealthCheck extends AdminAPI {

  @Override
  public void getAdminHealth(Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {

    super.getAdminHealth(okapiHeaders,  res -> {

      OutStream stream = new OutStream();
      stream.setData("OK");

      System.out.println(" --- this is an over ride of the health API by the config module "+res.result().getStatus());
      asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetAdminHealthResponse.withAnyOK(stream)));
    }, vertxContext);
  }

  @Override
  public void getAdminModuleStats(Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {

    super.getAdminModuleStats(okapiHeaders,  res -> {

      JsonObject o = new JsonObject(res.result().getEntity().toString());

      System.out.println(" --- this is an over ride of the Module Stats API by the config module ");
      asyncResultHandler.handle(io.vertx.core.Future.succeededFuture(GetAdminModuleStatsResponse.
        withPlainOK( o.encodePrettily() )));
    }, vertxContext);
  }
}

