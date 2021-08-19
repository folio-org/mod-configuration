package org.folio.rest;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import org.folio.postgres.testing.PostgresTesterContainer;
import org.folio.rest.client.TenantClient;
import org.folio.rest.jaxrs.model.TenantAttributes;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.utils.NetworkUtils;
import org.folio.rest.tools.utils.TenantInit;
import org.junit.AfterClass;
import org.junit.BeforeClass;

public class TestBase {
  public static Vertx deployVertx;
  public static int port;
  public static WebClient webClient;
  public static TenantClient tenantClient;
  public static final String TENANT_ID = "harvard";
  public static final String SCHEMA = TENANT_ID + "_mod_configuration";

  @BeforeClass
  public static void beforeAll(TestContext context) {
    PostgresClient.setPostgresTester(new PostgresTesterContainer());;

    deployVertx = Vertx.vertx();

    port = NetworkUtils.nextFreePort();

    WebClientOptions webClientOptions = new WebClientOptions().setDefaultPort(port);
    webClient = WebClient.create(deployVertx, webClientOptions);

    tenantClient = new TenantClient("http://localhost:" + port, TENANT_ID, null, webClient);

    DeploymentOptions options = new DeploymentOptions().setConfig(
      new JsonObject().put("http.port", port));

    dropSchema(SCHEMA)
    .compose(x -> deployVertx.deployVerticle(RestVerticle.class.getName(), options))
    .compose(x -> CheckUrlTest.postTenant())
    .onComplete(context.asyncAssertSuccess());
  }

  @AfterClass
  public static void afterAll(TestContext context) {
    dropSchema(SCHEMA).onComplete(context.asyncAssertSuccess());
  }

  public static Future<Void> dropSchema(String schema) {
    PostgresClient postgresClient = PostgresClient.getInstance(deployVertx);
    return postgresClient.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE")
        .compose(x -> postgresClient.execute("DROP ROLE IF EXISTS " + schema))
        .mapEmpty();
  }

  static Future<Void> postTenant() {
    try {
      TenantAttributes ta = new TenantAttributes();
      ta.setModuleTo("mod-configuration-1.0.0");
      TenantClient tenantClient = new TenantClient("http://localhost:" + port, TENANT_ID, null, webClient);
      return TenantInit.exec(tenantClient, ta, 60000);
    } catch (Exception e) {
      e.printStackTrace(System.err);
      return Future.failedFuture(e);
    }
  }

}
