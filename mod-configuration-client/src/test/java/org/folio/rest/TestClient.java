package org.folio.rest;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

import java.io.IOException;
import java.util.ArrayList;

import org.apache.commons.io.IOUtils;
import org.folio.rest.client.ConfigurationsClient;
import org.folio.rest.client.TenantClient;
import org.folio.rest.jaxrs.model.Config;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.utils.NetworkUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.fasterxml.jackson.databind.ObjectMapper;

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
  @Before
  public void setUp(TestContext context) throws IOException {
    vertx = Vertx.vertx();

    try {
      setupPostgres();
    } catch (Exception e) {
      e.printStackTrace();
    }
    Async async = context.async();
    DeploymentOptions options = new DeploymentOptions().setConfig(new JsonObject().put("http.port",
      port = NetworkUtils.nextFreePort()));
    vertx.deployVerticle(RestVerticle.class.getName(), options, context.asyncAssertSuccess(id -> {
      async.complete();
    }));

    cc = new ConfigurationsClient("localhost", port, "harvard");

  }

  private static void setupPostgres() throws Exception {
    PostgresClient.setIsEmbedded(true);
    PostgresClient.getInstance(vertx).startEmbeddedPostgres();
  }

  @After
  public void tearDown(TestContext context) {
    vertx.close(context.asyncAssertSuccess());
  }

  @Test
  public void test(TestContext context){

    try {
      Async async = context.async(2);
      TenantClient ac = new TenantClient("localhost", port, "harvard");
      ac.post(reply -> {
          try {
            postConfigs(async);
          } catch (Exception e) {
            e.printStackTrace();
          }
        });
    } catch (Exception e) {
      e.printStackTrace();
    }
  }


  public void getConfigs(Async async) {
    cc.getEntries("[{\"field\":\"'module'\",\"value\":\"CIRCULATION\",\"op\":\"=\"}]", null, null, 0, 10, "en", response -> {
      response.bodyHandler(body -> {
        System.out.println(body);
        async.countDown();
      });
    });
  }

  public void postConfigs(Async async) throws Exception {

    String content = getFile("kv_configuration.sample");
    Config conf =  new ObjectMapper().readValue(content, Config.class);
    cc.postEntries(null, conf, reply -> {
      reply.bodyHandler( handler -> {
        try {
          System.out.println(new String(handler.getBytes(), "UTF8"));
          async.countDown();
          getConfigs(async);
        } catch (Exception e) {
          e.printStackTrace();
          async.complete();
        }
      });
      System.out.println(reply.statusCode());
      System.out.println(reply.getHeader("Location"));
    });
  }

  private static String getFile(String filename) throws IOException {
    return IOUtils.toString(Test.class.getClassLoader().getResourceAsStream(filename), "UTF-8");
  }

}
