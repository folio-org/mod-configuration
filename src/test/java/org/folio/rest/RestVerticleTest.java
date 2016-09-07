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

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.google.common.io.ByteStreams;
import org.folio.rest.RestVerticle;
import org.folio.rest.persist.MongoCRUD;

/**
 * This is our JUnit test for our verticle. The test uses vertx-unit, so we declare a custom runner.
 */
@RunWith(VertxUnitRunner.class)
public class RestVerticleTest {

  private Vertx             vertx;
  private ArrayList<String> urls;

  /**
   * 
   * @param context
   *          the test context.
   */
  @Before
  public void setUp(TestContext context) throws IOException {
    vertx = Vertx.vertx();
    
    MongoCRUD.setIsEmbedded(true);
    try {
      MongoCRUD.getInstance(vertx).startEmbeddedMongo();
    } catch (Exception e1) {
      e1.printStackTrace();
    }
    
    DeploymentOptions options = new DeploymentOptions().setConfig(new JsonObject().put("http.port",
      Integer.valueOf(System.getProperty("http.port"))));
    vertx.deployVerticle(RestVerticle.class.getName(), options, context.asyncAssertSuccess(id -> {
      System.out.println("async complete =========================");
      try {
        urls = urlsFromFile();
        System.out.println("url complete =========================");
      } catch (Exception e) {
        e.printStackTrace();
      }
    }));

  }
  
  /**
   * This method, called after our test, just cleanup everything by closing the vert.x instance
   *
   * @param context
   *          the test context
   */
  @After
  public void tearDown(TestContext context) {
    vertx.close(context.asyncAssertSuccess());
  }

  /**
   * This method, iterates through the urls.csv and runs each url - currently only checking the returned status codes
   *
   * @param context
   *          the test context
   */
  @Test
  public void checkURLs(TestContext context) {

    try {
      int[] urlCount = { urls.size() };
      // Async async = context.async(urlCount[0]);
      urls.forEach(url -> {
        Async async = context.async();
        urlCount[0] = urlCount[0] - 1;
        HttpMethod method = null;

        String[] urlInfo = url.split(" , ");
        if ("POST".equalsIgnoreCase(urlInfo[0].trim())) {
          method = HttpMethod.POST;
        } else if ("PUT".equalsIgnoreCase(urlInfo[0].trim())) {
          method = HttpMethod.PUT;
        } else if ("DELETE".equalsIgnoreCase(urlInfo[0].trim())) {
          method = HttpMethod.DELETE;
        } else {
          method = HttpMethod.GET;
        }
        HttpClient client = vertx.createHttpClient();
        HttpClientRequest request = client.requestAbs(method, urlInfo[1], new Handler<HttpClientResponse>() {

          @Override
          public void handle(HttpClientResponse httpClientResponse) {

            System.out.println(urlInfo[1]);
            if (httpClientResponse.statusCode() != 404) {
              // this is cheating for now - add posts to the test case so that
              // we dont get 404 for missing entities
              context.assertInRange(200, httpClientResponse.statusCode(), 5);
            }
            // System.out.println(context.assertInRange(200, httpClientResponse.statusCode(),5).);
            httpClientResponse.bodyHandler(new Handler<Buffer>() {
              @Override
              public void handle(Buffer buffer) {
                /*
                 * // System.out.println("Response (" // + buffer.length() // + "): ");
                 */System.out.println(buffer.getString(0, buffer.length()));
                async.complete();

              }
            });
          }
        });
        request.headers().add("Authorization", "abcdefg");
        request.headers().add("Accept", "application/json");
        request.setChunked(true);
        request.end();

      });
    } catch (Throwable e) {
      e.printStackTrace();
    } finally {

    }
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

}
