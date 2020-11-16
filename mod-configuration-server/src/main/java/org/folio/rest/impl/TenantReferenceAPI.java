package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.folio.rest.jaxrs.model.TenantAttributes;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.utils.TenantTool;

public class TenantReferenceAPI extends TenantAPI {

  private static final Logger log = LoggerFactory.getLogger(TenantReferenceAPI.class);

  @Override
  public void postTenant(TenantAttributes tenantAttributes, Map<String, String> headers, Handler<AsyncResult<Response>> hndlr, Context cntxt) {
    log.info("postTenant");
    super.postTenant(tenantAttributes, headers, res -> {
      hndlr.handle(res);
    }, cntxt);

  }

  @Override
  public void getTenant(Map<String, String> headers, Handler<AsyncResult<Response>> hndlr, Context cntxt) {
    log.info("getTenant");
    super.getTenant(headers, hndlr, cntxt);
  }

  @Override
  public void deleteTenant(Map<String, String> headers, Handler<AsyncResult<Response>> hndlr, Context cntxt) {
    log.info("deleteTenant");
    super.deleteTenant(headers, res -> {
      Vertx vertx = cntxt.owner();
      String tenantId = TenantTool.tenantId(headers);
      PostgresClient.getInstance(vertx, tenantId)
        .closeClient(event -> hndlr.handle(res));
    }, cntxt);
  }
}
