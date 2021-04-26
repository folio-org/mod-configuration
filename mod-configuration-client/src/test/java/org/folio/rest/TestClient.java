package org.folio.rest;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;

import org.apache.commons.io.IOUtils;
import org.folio.postgres.testing.PostgresTesterContainer;
import org.folio.rest.client.ConfigurationsClient;
import org.folio.rest.client.TenantClient;
import org.folio.rest.jaxrs.model.Config;
import org.folio.rest.jaxrs.model.TenantAttributes;
import org.folio.rest.jaxrs.model.TenantJob;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.utils.NetworkUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.runner.RunWith;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;


/**
 * @author shale
 *
 */

@RunWith(VertxUnitRunner.class)
public class TestClient {

  private static Vertx      vertx;
  private ArrayList<String> urls;
  int                       port;
  ConfigurationsClient cc = null;
  TenantClient ac = null;

  @Before
  public void setUp(TestContext context) throws IOException {
    vertx = Vertx.vertx();

    PostgresClient.setPostgresTester(new PostgresTesterContainer());
    Async async = context.async();
    DeploymentOptions options = new DeploymentOptions().setConfig(new JsonObject().put("http.port",
      port = NetworkUtils.nextFreePort()));
    vertx.deployVerticle(RestVerticle.class.getName(), options, context.asyncAssertSuccess(id -> {
      async.complete();
    }));

    cc = new ConfigurationsClient("localhost", port, "harvard", "harvard");

  }

  @After
  public void tearDown(TestContext context) {
    Async async = context.async();
    vertx.close(context.asyncAssertSuccess( res-> {
      async.complete();
    }));
  }

  @org.junit.Test
  public void test1(TestContext context){

    try {
      Async async = context.async(2);
      ac = new TenantClient("localhost", port, "harvard", "harvard");
      TenantAttributes ta = new TenantAttributes();
      ta.setModuleTo("v1");
      ac.postTenant(ta, context.asyncAssertSuccess(res -> {
        context.assertEquals(201, res.statusCode());
        String jobId = res.bodyAsJson(TenantJob.class).getId();
        ac.getTenantByOperationId(jobId, 10000, context.asyncAssertSuccess(res2 -> {
          context.assertEquals(200, res2.statusCode());
          context.assertTrue(res2.bodyAsJson(TenantJob.class).getComplete());
          try {
            postConfigs(async, context);
          } catch (Exception e) {
            e.printStackTrace();
            context.fail(e);
          }
        }));
      }));
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public void getConfigs(Async async, TestContext context) {
    cc.getConfigurationsEntries("module==CIRCULATION", 0, 10, new String[]{"enabled:5" , "code"} ,"en",
      context.asyncAssertSuccess(response -> {
        if (response.statusCode() == 500) { // TODO: update this to be more specific (also in stable release)
          context.fail("status " + response.statusCode());
          return;
        }
        TenantAttributes ta = new TenantAttributes();
        ta.setPurge(true);
        try {
          ac.postTenant(ta, context.asyncAssertSuccess(res -> {
            if (res.statusCode() == 204) {
              async.complete();
              return;
            }
            context.assertEquals(201, res.statusCode());
            String jobId = res.bodyAsJson(TenantJob.class).getId();
            ac.getTenantByOperationId(jobId, 10000, context.asyncAssertSuccess(res2 -> {
              context.assertEquals(200, res2.statusCode());
              context.assertTrue(res2.bodyAsJson(TenantJob.class).getComplete());
              async.complete();
            }));
          }));
        } catch (Exception e) {
          context.fail(e);
          e.printStackTrace();
        }
      }));
  }

  public void postConfigs(Async async, TestContext context) throws Exception {
    String content = getFile("kv_configuration.sample");
    Config conf = new ObjectMapper().readValue(content, Config.class);
    cc.postConfigurationsEntries(null, conf, context.asyncAssertSuccess(reply -> {
      try {
        getConfigs(async, context);
      } catch (Exception e) {
        e.printStackTrace();
        async.complete();
      }
    }));
  }

  private static String getFile(String filename) throws IOException {
    return IOUtils.toString(TestClient.class.getClassLoader().getResourceAsStream(filename), "UTF-8");
  }

}
