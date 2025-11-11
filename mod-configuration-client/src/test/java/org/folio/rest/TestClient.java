package org.folio.rest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import io.vertx.ext.web.client.WebClient;
import org.apache.commons.io.IOUtils;
import org.folio.postgres.testing.PostgresTesterContainer;
import org.folio.rest.client.ConfigurationsClient;
import org.folio.rest.client.TenantClient;
import org.folio.rest.jaxrs.model.Config;
import org.folio.rest.jaxrs.model.TenantAttributes;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.utils.NetworkUtils;
import org.folio.rest.tools.utils.TenantInit;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;


/**
 * @author shale
 *
 */

@RunWith(VertxUnitRunner.class)
public class TestClient {

  private static Vertx      vertx;
  int                       port;
  ConfigurationsClient cc = null;
  TenantClient ac = null;
  WebClient webClient;

  @Before
  public void setUp(TestContext context) {
    vertx = Vertx.vertx();
    webClient = WebClient.create(vertx);

    port = NetworkUtils.nextFreePort();

    cc = new ConfigurationsClient("http://localhost:" + port, "harvard", "harvard", webClient);

    PostgresClient.setPostgresTester(new PostgresTesterContainer());
    DeploymentOptions options = new DeploymentOptions().setConfig(new JsonObject().put("http.port", port));
    vertx.deployVerticle(RestVerticle.class.getName(), options)
    .onComplete(context.asyncAssertSuccess());
  }

  @Test
  public void test1(TestContext context){
      ac = new TenantClient("http://localhost:" + port, "harvard", "harvard", webClient);
      TenantAttributes ta = new TenantAttributes();
      ta.setModuleTo("m-1");
      TenantInit.exec(ac, ta, 10000).onComplete(context.asyncAssertSuccess(res -> {
        postConfigs(context);
      }));
  }

  public void getConfigs(TestContext context) {
    cc.getConfigurationsEntries("module==CIRCULATION", 0, 10, new String[]{"enabled:5" , "code"} ,"en",
      context.asyncAssertSuccess(response -> {
        if (response.statusCode() == 500) { // TODO: update this to be more specific (also in stable release)
          context.fail("status " + response.statusCode());
          return;
        }
        TenantInit.purge(ac,  10000).onComplete(context.asyncAssertSuccess());
      }));
  }

  public void postConfigs(TestContext context) {
    try {
      String content = getFile("kv_configuration.sample");
      Config conf = new ObjectMapper().readValue(content, Config.class);
      cc.postConfigurationsEntries(null, conf, context.asyncAssertSuccess(reply -> getConfigs(context)));
    } catch (Exception e) {
      e.printStackTrace();
      context.fail(e);
    }
  }

  private static String getFile(String filename) throws IOException {
    return IOUtils.toString(TestClient.class.getClassLoader().getResourceAsStream(filename), StandardCharsets.UTF_8);
  }

}
