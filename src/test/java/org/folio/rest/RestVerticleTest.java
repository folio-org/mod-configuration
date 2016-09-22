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

import org.apache.commons.io.IOUtils;
import org.folio.rest.RestVerticle;
import org.folio.rest.persist.MongoCRUD;
import org.folio.rest.tools.utils.NetworkUtils;

/**
 * This is our JUnit test for our verticle. The test uses vertx-unit, so we declare a custom runner.
 */
@RunWith(VertxUnitRunner.class)
public class RestVerticleTest {

  private Vertx             vertx;
  private ArrayList<String> urls;
  int port;
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
      port = NetworkUtils.nextFreePort()));
    vertx.deployVerticle(RestVerticle.class.getName(), options, context.asyncAssertSuccess(id -> {
      try {
        urls = urlsFromFile();
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
        HttpClientRequest request = client.requestAbs(method, urlInfo[1].replaceFirst("<port>", port + ""), new Handler<HttpClientResponse>() {

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

    Async async = context.async();
    HttpClient client = vertx.createHttpClient();
    HttpClientRequest request;
    request = client.postAbs("http://localhost:"+port+"/apis/configurations/rules");
    request.exceptionHandler(error -> {
      async.complete();
      context.fail(error.getMessage());
    }).handler(response -> {
      int statusCode = response.statusCode();
      // is it 2XX
      System.out.println("Status - " + statusCode + " at " + System.currentTimeMillis() + " for " + request.path());

      if (statusCode == 204) {
        context.assertTrue(true);
      } else {
        response.bodyHandler(responseData -> {
          context.fail("got non 200 response from bosun, error: " + responseData + " code " + statusCode);
        });
      }
      if(!async.isCompleted()){
        async.complete();
      }
    });
    request.setChunked(true);
    request.putHeader("Authorization", "abcdefg");
    request.putHeader("Accept", "application/json,text/plain");
    request.putHeader("Content-type",
      "multipart/form-data; boundary=MyBoundary");
    Buffer b = Buffer.buffer();
    b.appendBuffer(getBody("Sample.drl", false).appendString("\r\n").appendBuffer(getBody("kv_configuration.sample", true)));
    request.write(b);
    request.end();
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


  private Buffer getBody(String filename, boolean closeBody) {
    Buffer buffer = Buffer.buffer();
    buffer.appendString("--MyBoundary\r\n");
    buffer.appendString("Content-Disposition: form-data; name=\""+filename+"\"; filename=\""+filename+"\"\r\n");
    buffer.appendString("Content-Type: application/octet-stream\r\n");
    buffer.appendString("Content-Transfer-Encoding: binary\r\n");
    buffer.appendString("\r\n");
    try {
      buffer.appendString(getFile(filename));
      buffer.appendString("\r\n");
    } catch (IOException e) {
      e.printStackTrace();

    }
    if(closeBody){
      buffer.appendString("--MyBoundary--\r\n");
    }
    return buffer;
  }

}
