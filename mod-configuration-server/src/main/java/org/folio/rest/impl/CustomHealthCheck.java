package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;

import java.util.Map;

import javax.ws.rs.core.Response;

/**
 * @author shale
 *
 */
public class CustomHealthCheck extends AdminAPI {

  @Override
  public void getAdminJstack(Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {
    /** just a test for now */
    super.getAdminJstack(okapiHeaders, asyncResultHandler, vertxContext);
  }



}
