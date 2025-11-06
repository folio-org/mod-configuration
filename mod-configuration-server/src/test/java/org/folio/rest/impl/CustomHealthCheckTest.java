package org.folio.rest.impl;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import java.util.Collections;
import org.folio.rest.RestVerticle;
import org.folio.rest.tools.utils.NetworkUtils;
import org.folio.rest.tools.utils.OutStream;
import org.folio.rest.tools.utils.VertxUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class CustomHealthCheckTest {
  private static Vertx vertx;
  private static String deployId;
  private static int port;

  @BeforeClass
  public static void setUp(TestContext context) {
    port = NetworkUtils.nextFreePort();
    DeploymentOptions options = new DeploymentOptions().setConfig(
       new JsonObject().put("http.port", port));
    vertx = VertxUtils.getVertxWithExceptionHandler();
    vertx.deployVerticle(RestVerticle.class.getName(), options)
    .onComplete(context.asyncAssertSuccess(deployId -> {
      CustomHealthCheckTest.deployId = deployId;
    }));
  }

  @AfterClass
  public static void tearDown(TestContext context) {
    vertx.undeploy(deployId)
    .onComplete(context.asyncAssertSuccess());
  }

  @Test
  public void getAdminHealth(TestContext context) {
    CustomHealthCheck customHealthCheck = new CustomHealthCheck();
    customHealthCheck.getAdminHealth(Collections.emptyMap(), result -> {
      context.assertTrue(result.succeeded());
      OutStream out = (OutStream) result.result().getEntity();
      context.assertEquals(out.getData(), "OK");
    }, null);
  }

}
