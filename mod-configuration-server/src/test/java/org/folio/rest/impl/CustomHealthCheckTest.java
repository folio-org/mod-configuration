package org.folio.rest.impl;

import java.util.Collections;

import org.folio.rest.RestVerticle;
import org.folio.rest.tools.utils.OutStream;
import org.folio.rest.tools.utils.VertxUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.vertx.core.Vertx;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class CustomHealthCheckTest {
  private static Vertx vertx;
  private static String deployId;

  @BeforeClass
  public static void setUp(TestContext context) {
    Async async = context.async();
    vertx = VertxUtils.getVertxWithExceptionHandler();
    vertx.deployVerticle(RestVerticle.class.getName(), done -> {
      deployId = done.result();
      async.complete();
    });
  }

  @AfterClass
  public static void tearDown(TestContext context) {
    vertx.undeploy(deployId, context.asyncAssertSuccess());
  }

  @Test
  public void getAdminHealth(TestContext context) throws Exception {
    CustomHealthCheck customHealthCheck = new CustomHealthCheck();
    customHealthCheck.getAdminHealth(Collections.emptyMap(), result -> {
      context.assertTrue(result.succeeded());
      OutStream out = (OutStream) result.result().getEntity();
      context.assertEquals(out.getData(), "OK");
    }, null);
  }

  @Test
  public void getAdminModuleStats(TestContext context) throws Exception {
    CustomHealthCheck customHealthCheck = new CustomHealthCheck();
    customHealthCheck.getAdminModuleStats(Collections.emptyMap(), result -> {
      context.assertTrue(result.succeeded());
      String json = (String) result.result().getEntity();
      context.assertEquals(json, "{ }");
    }, null);
  }
}
