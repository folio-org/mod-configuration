package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.TenantAttributes;

public class TenantRefAPI extends TenantAPI {
  @Validate
  @Override
  public void postTenant(TenantAttributes tenantAttributes, Map<String, String> headers,
                         Handler<AsyncResult<Response>> handler, Context context)  {
    // https://www.postgresql.org/docs/current/runtime-config-preset.html#GUC-SERVER-VERSION-NUM
    context.putLocal("postgres_min_version_num", "100000");
    context.putLocal("postgres_min_version", "10.0");  // human readable, for error message only
    super.postTenant(tenantAttributes, headers, handler, context);
  }
}
