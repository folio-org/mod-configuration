package org.folio.rest;


import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.apache.commons.io.IOUtils;
import org.folio.rest.client.TenantClient;
import org.folio.rest.jaxrs.model.Config;
import org.folio.rest.jaxrs.model.Metadata;
import org.folio.rest.jaxrs.model.TenantAttributes;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.security.AES;
import org.folio.rest.tools.utils.NetworkUtils;
import org.junit.*;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.*;

/**
 * This is our JUnit test for our verticle. The test uses vertx-unit, so we declare a custom runner.
 */
@RunWith(VertxUnitRunner.class)
public class RestVerticleTest {
  private static Locale oldLocale;
  private static Vertx vertx;
  private ArrayList<String> urls;
  private int port;
  private TenantClient tClient = null;

  static {
    System.setProperty(LoggerFactory.LOGGER_DELEGATE_FACTORY_CLASS_NAME,
      "io.vertx.core.logging.Log4jLogDelegateFactory");
  }

  @BeforeClass
  public static void setUpClass() {
    oldLocale = Locale.getDefault();
    Locale.setDefault(Locale.US);
  }

  @AfterClass
  public static void tearDownClass() {
    Locale.setDefault(oldLocale);
  }

  @Before
  public void setUp(TestContext context) {
    vertx = Vertx.vertx();

    try {
      AES.setSecretKey("b2+S+X4F/NFys/0jMaEG1A");
      setupPostgres();
    } catch (Exception e) {
      e.printStackTrace();
    }

    Async async = context.async();

    port = NetworkUtils.nextFreePort();

    tClient = new TenantClient("localhost", port, "harvard", "harvard");

/*    port = 8888;//NetworkUtils.nextFreePort();

    AdminClient aClient = new AdminClient("localhost", port, "myuniversity");
    try {
      aClient.postImportSQL(
        RestVerticleTest.class.getClassLoader().getResourceAsStream("create_config.sql"), reply -> {
          async.complete();
        });
    } catch (Exception e) {
      e.printStackTrace();
    }*/
    DeploymentOptions options = new DeploymentOptions().setConfig(new JsonObject().put("http.port",
      port));
    vertx.deployVerticle(RestVerticle.class.getName(), options, context.asyncAssertSuccess(id -> {
      try {
        TenantAttributes ta = new TenantAttributes();
        ta.setModuleFrom("v1");
        //ta.setModuleTo("v2");
        tClient.post(ta, response -> {
          if(422 == response.statusCode()){
            try {
              tClient.post(null, responseHandler -> {
                responseHandler.bodyHandler( body -> {
                  System.out.println(body.toString());
                  async.complete();
                });
              });
            } catch (Exception e) {
              context.fail(e.getMessage());
            }
          }
          else{
            context.fail("expected code 422 for validation error but received " + response.statusCode());
          }
        });

      } catch (Exception e) {
        e.printStackTrace();
      }
    }));

  }

  private static void setupPostgres() throws IOException {
    PostgresClient.setIsEmbedded(true);
    PostgresClient.setEmbeddedPort(NetworkUtils.nextFreePort());
    PostgresClient.getInstance(vertx).startEmbeddedPostgres();
  }

  @After
  public void tearDown(TestContext context) {
    Async async = context.async();
    tClient.delete( reply -> {
      reply.bodyHandler( body2 -> {
        System.out.println(body2.toString());
        vertx.close(context.asyncAssertSuccess( res-> {
          PostgresClient.stopEmbeddedPostgres();
          async.complete();
        }));
      });
    });
  }

  /**
   * This method, iterates through the urls.csv and runs each url - currently only checking the returned status codes
   */
  @Test
  public void checkURLs(TestContext context) {

    try {
      //save config entry
      String content = getFile("kv_configuration.sample");
      Config conf =  new ObjectMapper().readValue(content, Config.class);

      mutateURLs("http://localhost:" + port + "/configurations/entries", context, HttpMethod.POST,
        content, "application/json", 201);

      //save config entry with value being a base64 encoded file
      String attachment = Base64.getEncoder().encodeToString(getFile("Sample.drl").getBytes());
      conf.setValue(attachment);

      mutateURLs("http://localhost:" + port + "/configurations/entries", context, HttpMethod.POST,
        new ObjectMapper().writeValueAsString(conf), "application/json", 201);

      conf.setEnabled(false);

      mutateURLs("http://localhost:" + port + "/configurations/entries", context, HttpMethod.POST,
        new ObjectMapper().writeValueAsString(conf), "application/json", 201);

      mutateURLs("http://localhost:" + port + "/configurations/entries", context, HttpMethod.POST,
        new ObjectMapper().writeValueAsString(conf), "application/json", 201);

      //attempt to delete invalud id (not uuid)
      mutateURLs("http://localhost:" + port + "/configurations/entries/123456", context, HttpMethod.DELETE,
        "", "application/json", 404);

      mutateURLs("http://localhost:" + port + "/admin/kill_query?pid=11", context, HttpMethod.DELETE,
        "", "application/json", 404);

      //check read only
      Config conf2 =  new ObjectMapper().readValue(content, Config.class);
      Metadata md = new Metadata();
      md.setCreatedByUserId("123456");
      conf2.setMetadata(md);
      mutateURLs("http://localhost:" + port + "/configurations/entries", context, HttpMethod.POST,
        new ObjectMapper().writeValueAsString(conf2), "application/json", 422);

      md.setCreatedByUserId("2b94c631-fca9-a892-c730-03ee529ffe2a");
      md.setCreatedDate(new Date());
      md.setUpdatedDate(new Date());
      conf2.setModule("NOTHING");
      String updatedConf = new ObjectMapper().writeValueAsString(conf2);
      System.out.println(updatedConf);
      mutateURLs("http://localhost:" + port + "/configurations/entries", context, HttpMethod.POST,
        updatedConf, "application/json", 201);

      mutateURLs("http://localhost:" + port + "/admin/loglevel?level=FINE&java_package=org.folio.rest.persist", context,
        HttpMethod.PUT,"",  "application/json", 200);

/*      conf2.setMetadata(null);
      conf2.setModule("BATCH");
      conf2.setValue("what1");
      Config conf3 = (Config)BeanUtils.cloneBean(conf2);
      conf3.setValue("what2");
      Configs c = new Configs();
      List<Config> configArray = new ArrayList<>();
      configArray.add(conf2);
      configArray.add(conf3);
      c.setConfigs(configArray);
      mutateURLs("http://localhost:" + port + "/configurations/entries", context, HttpMethod.PUT,
        new ObjectMapper().writeValueAsString(c), "application/json", 204);*/

    } catch (Exception e) {
      e.printStackTrace();
      context.assertTrue(false, e.getMessage());
    }

    try {
      Thread.sleep(2000);
    } catch (InterruptedException e1) {
      // TODO Auto-generated catch block
      e1.printStackTrace();
    }

    Async async = context.async();
    PostgresClient.getInstance(vertx, "harvard").persistentlyCacheResult("mytablecache",
      "select * from harvard_mod_configuration.config_data where jsonb->>'config_name' = 'validation_rules'",  reply -> {
        if(reply.succeeded()){
          PostgresClient.getInstance(vertx, "harvard").select("select * from harvard_mod_configuration.mytablecache", r3 -> {
            System.out.println(r3.result().getResults().size());
            PostgresClient.getInstance(vertx, "harvard").removePersistentCacheResult("mytablecache",  r4 -> {
              System.out.println(r4.succeeded());

              /** this will probably cause a deadlock as the saveBatch runs within a transaction */

             /*
             List<Object> a = Arrays.asList(new Object[]{new JsonObject("{\"module1\": \"CIRCULATION\"}"),
                  new JsonObject("{\"module1\": \"CIRCULATION15\"}"), new JsonObject("{\"module1\": \"CIRCULATION\"}")});
              try {
                PostgresClient.getInstance(vertx, "harvard").saveBatch("config_data", a, reply1 -> {
                  if(reply1.succeeded()){
                    System.out.println(new io.vertx.core.json.JsonArray( reply1.result().getResults() ).encodePrettily());
                  }
                  async.complete();
                  });
              } catch (Exception e1) {
                e1.printStackTrace();
              }*/
              async.complete();

            });
          });
        }
      });

    try {
      urls = urlsFromFile();
      Thread.sleep(2000);
    } catch (Exception e) {
      e.printStackTrace();
    }

    //run get queries from the csv file
    runGETURLoop(context);

  }

  private void runGETURLoop(TestContext context){
    try {
      int[] urlCount = { urls.size() };
      urls.forEach(url -> {
        Async async = context.async();
        String[] urlInfo = url.split(" , ");
        HttpClient client = vertx.createHttpClient();
        HttpClientRequest request = client.requestAbs(HttpMethod.GET,
          urlInfo[1].trim().replaceFirst("<port>", port + ""), new Handler<HttpClientResponse>() {
          @Override
          public void handle(HttpClientResponse httpClientResponse) {
            int statusCode = httpClientResponse.statusCode();
            System.out.println("Status - " + statusCode + " " + urlInfo[1]);
            if (httpClientResponse.statusCode() != Integer.parseInt(urlInfo[3])) {
              context.fail("expected " + Integer.parseInt(urlInfo[3]) + " , got " + httpClientResponse.statusCode());
              async.complete();
            }
            httpClientResponse.bodyHandler(new Handler<Buffer>() {
              @Override
              public void handle(Buffer buffer) {
                if(buffer.length() < 5 || httpClientResponse.statusCode() != 200){
                  //assume empty body / empty array of data
                  async.complete();
                }
                else{
                  try{
                    System.out.println(buffer.toString());
                    int records = new JsonObject(buffer.getString(0, buffer.length())).getInteger("totalRecords");
                    System.out.println("-------->"+records);
                    if(httpClientResponse.statusCode() == 200){
                      if(records != Integer.parseInt(urlInfo[4])){
                        context.fail(urlInfo[1] + " expected record count: " + urlInfo[4] + ", returned record count: " + records);
                        async.complete();
                      }
                      else{
                        async.complete();
                      }
                    }
/*                    aClient.getModuleStats( res -> {
                      res.bodyHandler( b -> {
                        System.out.println(urlInfo[1] + "  "+b.toString());
                        aClient.getHealth( r -> {
                          r.bodyHandler( bh -> {
                            System.out.println(urlInfo[1] + "  "+bh.toString());
                            async.complete();
                          });
                        });
                      });
                    });*/
                  }
                  catch(Exception e){
                    e.printStackTrace();
                    context.fail(e.getMessage());
                  }
                }
              }
            });
          }
        });
        request.putHeader("X-Okapi-Request-Id", "999999999999");
        request.putHeader("x-okapi-tenant", "harvard");
        request.headers().add("Authorization", "harvard");
        request.headers().add("Accept", "application/json");
        request.setChunked(true);
        request.end();
      });
    } catch (Throwable e) {
      e.printStackTrace();
    }
  }

  private void mutateURLs(String api, TestContext context, HttpMethod method, String content,
      String contentType, int errorCode) {
    Async async = context.async();
    HttpClient client = vertx.createHttpClient();
    HttpClientRequest request;
    Buffer buffer = Buffer.buffer(content);

    if (method == HttpMethod.POST) {
      request = client.postAbs(api);
    }
    else if (method == HttpMethod.DELETE) {
      request = client.deleteAbs(api);
    }
    else if (method == HttpMethod.GET) {
      request = client.getAbs(api);
    }
    else {
      request = client.putAbs(api);
    }
    request.exceptionHandler(error -> {
      async.complete();
      context.fail(error.getMessage());
    }).handler(response -> {
      response.headers().forEach( header -> {
        System.out.println(header.getKey() + " " + header.getValue());
      });
      int statusCode = response.statusCode();
      if(method == HttpMethod.POST && statusCode == 201){
        try {
          System.out.println("Location - " + response.getHeader("Location"));
          //String content2 = getFile("kv_configuration.sample");
          Config conf =  new ObjectMapper().readValue(content, Config.class);
          conf.setDescription(conf.getDescription());
          mutateURLs("http://localhost:" + port + response.getHeader("Location"), context, HttpMethod.PUT,
            new ObjectMapper().writeValueAsString(conf), "application/json", 204);
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
      System.out.println("Status - " + statusCode + " at " + System.currentTimeMillis() + " for " + api);
      if(errorCode == statusCode){
        context.assertTrue(true);
      }
      else if(errorCode == 0){
        //currently dont care about return value
        context.assertTrue(true);
      }
      else {
        context.fail("expected " + errorCode +" code, but got " + statusCode);
      }
      if(!async.isCompleted()){
        async.complete();
      }
      System.out.println("complete");
    });
    request.setChunked(true);
    request.putHeader("X-Okapi-Request-Id", "999999999999");
    request.putHeader("Authorization", "harvard");
    request.putHeader("x-okapi-tenant", "harvard");
    request.putHeader("Accept", "application/json,text/plain");
    request.putHeader("Content-type", contentType);
    request.end(buffer);
  }

  private ArrayList<String> urlsFromFile() {
    ArrayList<String> ret = new ArrayList<>();

    try (Scanner scanner = new Scanner(getClass().getResourceAsStream("/urls.csv"))) {
    while(scanner.hasNext()) {
        ret.add(scanner.nextLine());
      }
    }

    return ret;
  }

  private String getFile(String filename) throws IOException {
    return IOUtils.toString(getClass().getClassLoader().getResourceAsStream(filename), "UTF-8");
  }
}
