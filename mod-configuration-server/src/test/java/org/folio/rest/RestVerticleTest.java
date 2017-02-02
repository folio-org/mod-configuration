package org.folio.rest;


import io.vertx.core.DeploymentOptions;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Base64;

import org.apache.commons.io.IOUtils;
import org.folio.rest.client.AdminClient;
import org.folio.rest.client.TenantClient;
import org.folio.rest.jaxrs.model.Config;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.security.AES;
import org.folio.rest.tools.utils.NetworkUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.ByteStreams;

/**
 * This is our JUnit test for our verticle. The test uses vertx-unit, so we declare a custom runner.
 */
@RunWith(VertxUnitRunner.class)
public class RestVerticleTest {

  private static Vertx      vertx;
  private ArrayList<String> urls;
  int                       port;
  TenantClient tClient = null;
  AdminClient aClient  = null;
  /**
   *
   * @param context
   *          the test context.
   */
  @Before
  public void setUp(TestContext context) throws IOException {
    vertx = Vertx.vertx();

    try {
      AES.setSecretKey("b2+S+X4F/NFys/0jMaEG1A");
      setupPostgres();
    } catch (Exception e) {
      e.printStackTrace();
    }

    Async async = context.async();

    port = NetworkUtils.nextFreePort();

    aClient = new AdminClient("localhost", port, "harvard");
    tClient = new TenantClient("localhost", port, "harvard");

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
        tClient.post( response -> {
          response.bodyHandler( body -> {
            System.out.println(body.toString());
            async.complete();
          });
        });

      } catch (Exception e) {
        e.printStackTrace();
      }
    }));

  }

  private static void setupPostgres() throws Exception {
    PostgresClient.setIsEmbedded(true);
    PostgresClient.getInstance(vertx).startEmbeddedPostgres();
  }

  /**
   * This method, called after our test, just cleanup everything by closing the vert.x instance
   *
   * @param context
   *          the test context
   */
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
   *
   * @param context the test context
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

      //delete non existent record
      mutateURLs("http://localhost:" + port + "/configurations/entries/123456", context, HttpMethod.DELETE,
        "", "application/json", 404);

      mutateURLs("http://localhost:" + port + "/admin/kill_query?pid=11", context, HttpMethod.DELETE,
        "", "application/json", 404);

    } catch (Exception e) {
      e.printStackTrace();
    }

    Async async = context.async();
    PostgresClient.getInstance(vertx, "harvard").persistentlyCacheResult("mytablecache",
      "select * from harvard_configuration.config_data where jsonb->>'config_name' = 'validation_rules'",  reply -> {
        if(reply.succeeded()){
          PostgresClient.getInstance(vertx, "harvard").select("select * from harvard_configuration.mytablecache", r3 -> {
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
            if (httpClientResponse.statusCode() == 200) {
              context.assertTrue(true);
            }
            else if(httpClientResponse.statusCode() == 404){
              context.assertTrue(true);
              async.complete();
            }
            httpClientResponse.bodyHandler(new Handler<Buffer>() {
              @Override
              public void handle(Buffer buffer) {
                if(buffer.length() < 5){
                  //assume empty body / empty array of data
                  async.complete();
                }
                else{
                  int records = new JsonObject(buffer.getString(0, buffer.length())).getInteger("total_records");
                  System.out.println("-------->"+records);
                  System.out.println(buffer.toString());
                  aClient.getModuleStats( res -> {
                    res.bodyHandler( b -> {
                      System.out.println(urlInfo[1] + "  "+b.toString());
                      aClient.getHealth( r -> {
                        r.bodyHandler( bh -> {
                          System.out.println(urlInfo[1] + "  "+bh.toString());
                          async.complete();
                        });
                      });
                    });
                  });
                }
              }
            });
          }
        });
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

  /**
   * for POST / PUT / DELETE
   * @param api
   * @param context
   * @param method
   * @param content
   * @param id
   */
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
    else {
      request = client.putAbs(api);
    }
    request.exceptionHandler(error -> {
      async.complete();
      context.fail(error.getMessage());
    }).handler(response -> {
      int statusCode = response.statusCode();
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
    request.putHeader("Authorization", "harvard");
    request.putHeader("x-okapi-tenant", "harvard");
    request.putHeader("Accept", "application/json,text/plain");
    request.putHeader("Content-type", contentType);
    request.end(buffer);
  }

  private ArrayList<String> urlsFromFile() throws IOException {
    ArrayList<String> ret = new ArrayList<String>();
    byte[] content = ByteStreams.toByteArray(getClass().getResourceAsStream("/urls.csv"));
    InputStream is = null;
    BufferedReader bfReader = null;
    try {
      is = new ByteArrayInputStream(content);
      bfReader = new BufferedReader(new InputStreamReader(is));
      String temp = null;
      while ((temp = bfReader.readLine()) != null) {
        ret.add(temp);
      }
    } catch (IOException e) {
      e.printStackTrace();
    } finally {
      try {
        if (is != null)
          is.close();
      } catch (Exception ex) {

      }
    }
    return ret;
  }

  private String getFile(String filename) throws IOException {
    return IOUtils.toString(getClass().getClassLoader().getResourceAsStream(filename), "UTF-8");
  }

}
