package org.folio.rest;


import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
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
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * This is our JUnit test for our verticle. The test uses vertx-unit, so we declare a custom runner.
 */
@RunWith(VertxUnitRunner.class)
public class RestVerticleTest {
  private static final String SECRET_KEY = "b2+S+X4F/NFys/0jMaEG1A";
  private static final String TENANT_ID = "harvard";
  private static final String USER_ID = "79ff2a8b-d9c3-5b39-ad4a-0a84025ab085";
  private static final String TOKEN = "eyJhbGciOiJIUzUxMiJ9eyJzdWIiOiJhZG1pbiIsInVzZXJfaWQiOiI3OWZmMmE4Yi1kOWMzLTViMzktYWQ0YS0wYTg0MDI1YWIwODUiLCJ0ZW5hbnQiOiJ0ZXN0X3RlbmFudCJ9BShwfHcNClt5ZXJ8ImQTMQtAM1sQEnhsfWNmXGsYVDpuaDN3RVQ9";

  private static Locale oldLocale;
  private static Vertx vertx;
  private static int port;
  private static TenantClient tClient = null;

  static {
    System.setProperty(LoggerFactory.LOGGER_DELEGATE_FACTORY_CLASS_NAME,
      "io.vertx.core.logging.Log4jLogDelegateFactory");
  }

  @BeforeClass
  public static void beforeAll(TestContext context) {
    oldLocale = Locale.getDefault();
    Locale.setDefault(Locale.US);

    vertx = Vertx.vertx();

    try {
      AES.setSecretKey(SECRET_KEY);
      setupPostgres();
    } catch (Exception e) {
      e.printStackTrace();
    }

    Async async = context.async();

    port = NetworkUtils.nextFreePort();

    tClient = new TenantClient("localhost", port, TENANT_ID, TOKEN);

    DeploymentOptions options = new DeploymentOptions().setConfig(
      new JsonObject().put("http.port", port));

    vertx.deployVerticle(RestVerticle.class.getName(), options, context.asyncAssertSuccess(id -> {
      try {
        TenantAttributes ta = new TenantAttributes();
        ta.setModuleFrom("v1");
        tClient.post(ta, response -> {
          if(422 == response.statusCode()){
            try {
              tClient.post(null, responseHandler ->
                responseHandler.bodyHandler(body -> {
                System.out.println(body.toString());
                async.complete();
              }));
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

  @AfterClass
  public static void afterAll(TestContext context) {
    Async async = context.async();
    tClient.delete(reply -> reply.bodyHandler(body -> {
      System.out.println(body.toString());
      vertx.close(context.asyncAssertSuccess(res-> {
        PostgresClient.stopEmbeddedPostgres();
        async.complete();
      }));
    }));

    Locale.setDefault(oldLocale);
  }

  @Before
  public void beforeEach()
    throws InterruptedException,
    ExecutionException,
    TimeoutException {

    deleteAllConfigurationRecordsExceptLocales()
      .thenComposeAsync(v -> deleteAllConfigurationAuditRecordsExceptLocales())
      .get(5, TimeUnit.SECONDS);
  }

  @Test
  public void canCreateConfigurationRecord(TestContext testContext) {
    final Async async = testContext.async();

    JsonObject configRecord = new JsonObject()
      .put("module", "CHECKOUT")
      .put("configName", "other_settings")
      .put("description", "Whether audio alerts should be made during ckeckout")
      .put("code", "audioAlertsEnabled")
      .put("value", true);

    final CompletableFuture<Response> postCompleted = post(
      "http://localhost:" + port + "/configurations/entries",
      configRecord.encodePrettily());

    postCompleted.thenAccept(response -> {
      try {
        testContext.assertEquals(201, response.statusCode,
          String.format("Unexpected status code: '%s': '%s'", response.getStatusCode(),
            response.getBody()));

        System.out.println(String.format("Create Response: '%s'", response.getBody()));

        JsonObject createdRecord = new JsonObject(response.getBody());

        testContext.assertEquals("CHECKOUT", createdRecord.getString("module"));
        testContext.assertEquals("other_settings", createdRecord.getString("configName"));
        testContext.assertEquals("audioAlertsEnabled", createdRecord.getString("code"));
        //TODO: Investigate why boolean value gets converted into a string
        testContext.assertEquals("true", createdRecord.getString("value"));

        testContext.assertTrue(createdRecord.containsKey("metadata"),
          String.format("Should contain change metadata property: '%s'",
            createdRecord.encodePrettily()));

        final JsonObject changeMetadata = createdRecord.getJsonObject("metadata");

        testContext.assertTrue(changeMetadata.containsKey("createdDate"),
          String.format("Should contain created date property: '%s'", changeMetadata));

        testContext.assertTrue(changeMetadata.containsKey("createdByUserId"),
          String.format("Should contain created by property: '%s'", changeMetadata));

        testContext.assertTrue(changeMetadata.containsKey("updatedDate"),
          String.format("Should contain updated date property: '%s'", changeMetadata));

        testContext.assertTrue(changeMetadata.containsKey("updatedByUserId"),
          String.format("Should contain updated by property: '%s'", changeMetadata));
      }
      catch(Exception e) {
        testContext.fail(e);
      }
      finally {
        async.complete();
      }
    });
  }

  @Test
  public void canGetConfigurationRecords(TestContext testContext) {
    final Async async = testContext.async();

    final ArrayList<CompletableFuture<Response>> allCreated = new ArrayList<>();

    JsonObject firstConfigRecord = new JsonObject()
      .put("module", "CHECKOUT")
      .put("configName", "other_settings")
      .put("description", "Whether audio alerts should be made during ckeckout")
      .put("code", "audioAlertsEnabled")
      .put("value", true);

    allCreated.add(post(
      "http://localhost:" + port + "/configurations/entries",
      firstConfigRecord.encodePrettily()));

    JsonObject secondConfigRecord = new JsonObject()
      .put("module", "CHECKOUT")
      .put("configName", "other_settings")
      .put("description", "Whether audio alerts should be made during ckeckout")
      .put("code", "checkoutTimeoutDuration")
      .put("value", 3);

    allCreated.add(post(
      "http://localhost:" + port + "/configurations/entries",
      secondConfigRecord.encodePrettily()));

    allOf(allCreated).thenComposeAsync(v ->
      //Must filter to only check out module entries due to default locale records
      get("http://localhost:" + port + "/configurations/entries?query=module==CHECKOUT"))
    .thenAccept(response -> {
      try {
        testContext.assertEquals(200, response.statusCode,
          String.format("Unexpected status code: '%s': '%s'", response.getStatusCode(),
            response.getBody()));

        JsonObject wrappedRecords = new JsonObject(response.getBody());

        testContext.assertEquals(2, wrappedRecords.getInteger("totalRecords"));
      }
      catch(Exception e) {
        testContext.fail(e);
      }
      finally {
        async.complete();
      }
    });
  }
  
  @Test
  public void canChangeLogLevel(TestContext context) {
    mutateURLs("http://localhost:" + port +
        "/admin/loglevel?level=FINE&java_package=org.folio.rest.persist",
      context, HttpMethod.PUT,"",  "application/json", 200);
  }

  @Test
  public void canUsePersistentCaching(TestContext context) {
    Async async = context.async();

    final PostgresClient postgresClient = PostgresClient.getInstance(vertx, TENANT_ID);

    postgresClient.persistentlyCacheResult("mytablecache",
      "select * from harvard_mod_configuration.config_data where jsonb->>'config_name' = 'validation_rules'",  reply -> {
        if(reply.succeeded()){
          postgresClient.select("select * from harvard_mod_configuration.mytablecache", r3 -> {
            System.out.println(r3.result().getResults().size());
            postgresClient.removePersistentCacheResult("mytablecache", r4 -> {
              System.out.println(r4.succeeded());

              /* this will probably cause a deadlock as the saveBatch runs within a transaction */

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
  }

  /**
   * This method, iterates through the urls.csv and runs each url - currently only checking the returned status codes
   */
  @Test
  public void checkURLs(TestContext context) {
    createSampleRecords(context);
    waitForTwoSeconds();
    checkResultsFromVariousUrls(context);
  }

  private void checkResultsFromVariousUrls(TestContext context) {
    runGETURLoop(context, urlsFromFile());
  }

  private void waitForTwoSeconds() {
    try {
      Thread.sleep(2000);
    } catch (InterruptedException e1) {
      // TODO Auto-generated catch block
      e1.printStackTrace();
    }
  }

  private void createSampleRecords(TestContext context) {
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

    } catch (Exception e) {
      e.printStackTrace();
      context.assertTrue(false, e.getMessage());
    }
  }

  private void runGETURLoop(TestContext context, ArrayList<String> urlsToCheck){
    try {
      urlsToCheck.forEach(url -> {
        Async async = context.async();
        String[] urlInfo = url.split(" , ");
        HttpClient client = vertx.createHttpClient();
        HttpClientRequest request = client.requestAbs(HttpMethod.GET,
          urlInfo[1].trim().replaceFirst("<port>", port + ""), httpClientResponse -> {
            int statusCode = httpClientResponse.statusCode();
            System.out.println("Status - " + statusCode + " " + urlInfo[1]);
            if (httpClientResponse.statusCode() != Integer.parseInt(urlInfo[3])) {
              context.fail("expected " + Integer.parseInt(urlInfo[3]) + " , got " + httpClientResponse.statusCode());
              async.complete();
            }
            httpClientResponse.bodyHandler(buffer -> {
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
                }
                catch(Exception e){
                  e.printStackTrace();
                  context.fail(e.getMessage());
                }
              }
            });
          });
        request.putHeader("X-Okapi-Request-Id", "999999999999");
        request.headers().add("Authorization", TENANT_ID);
        request.putHeader("x-Okapi-Tenant", TENANT_ID);
        request.putHeader("x-Okapi-Token", TOKEN);
        request.putHeader("x-Okapi-User-Id", USER_ID);
        request.headers().add("Accept", "application/json");
        request.setChunked(true);
        request.end();
      });
    } catch (Throwable e) {
      e.printStackTrace();
    }
  }

  private void mutateURLs(
    String url,
    TestContext context,
    HttpMethod method,
    String content,
    String contentType,
    int expectedStatusCode) {

    Async async = context.async();
    HttpClient client = vertx.createHttpClient();
    HttpClientRequest request;
    Buffer buffer = Buffer.buffer(content);

    if (method == HttpMethod.POST) {
      request = client.postAbs(url);
    }
    else if (method == HttpMethod.DELETE) {
      request = client.deleteAbs(url);
    }
    else if (method == HttpMethod.GET) {
      request = client.getAbs(url);
    }
    else {
      request = client.putAbs(url);
    }
    request.exceptionHandler(error -> {
      async.complete();
      context.fail(error.getMessage());
    }).handler(response -> {
      response.headers().forEach( header ->
        System.out.println(header.getKey() + " " + header.getValue()));

      int statusCode = response.statusCode();
      if(method == HttpMethod.POST && statusCode == 201){
        try {
          System.out.println("Location - " + response.getHeader("Location"));
          Config conf =  new ObjectMapper().readValue(content, Config.class);
          conf.setDescription(conf.getDescription());
          mutateURLs("http://localhost:" + port + response.getHeader("Location"), context, HttpMethod.PUT,
            new ObjectMapper().writeValueAsString(conf), "application/json", 204);
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
      System.out.println("Status - " + statusCode + " at " + System.currentTimeMillis() + " for " + url);
      if(expectedStatusCode == statusCode){
        context.assertTrue(true);
      }
      else if(expectedStatusCode == 0){
        //currently don't care about return value
        context.assertTrue(true);
      }
      else {
        context.fail("expected " + expectedStatusCode +" code, but got " + statusCode);
      }
      if(!async.isCompleted()){
        async.complete();
      }
      System.out.println("complete");
    });
    request.setChunked(true);
    request.putHeader("X-Okapi-Request-Id", "999999999999");
    request.putHeader("Authorization", TENANT_ID);
    request.putHeader("x-Okapi-Tenant", TENANT_ID);
    request.putHeader("x-Okapi-Token", TOKEN);
    request.putHeader("x-Okapi-User-Id", USER_ID);
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

  private static void setupPostgres() throws IOException {
    PostgresClient.setIsEmbedded(true);
    PostgresClient.setEmbeddedPort(NetworkUtils.nextFreePort());
    PostgresClient.getInstance(vertx).startEmbeddedPostgres();
  }

  private CompletableFuture<Void> deleteAllConfigurationRecordsExceptLocales() {
    return deleteAllConfigurationRecordsFromTableExceptLocales("config_data");
  }

  private CompletableFuture<Void> deleteAllConfigurationAuditRecordsExceptLocales() {
    return deleteAllConfigurationRecordsFromTableExceptLocales("audit_config_data");
  }

  private CompletableFuture<Void> deleteAllConfigurationRecordsFromTableExceptLocales(
    String audit_config_data) {

    CompletableFuture<Void> allDeleted = new CompletableFuture<>();

    final PostgresClient postgresClient = PostgresClient.getInstance(vertx, TENANT_ID);

    //Do not delete the sample records created from
    postgresClient.mutate(String.format("DELETE FROM %s_%s.%s WHERE jsonb->>'configName' != 'locale'",
      TENANT_ID, "mod_configuration", audit_config_data), reply -> {
      if (reply.succeeded()) {
        allDeleted.complete(null);
      } else {
        allDeleted.completeExceptionally(reply.cause());
      }
    });

    return allDeleted;
  }

  private CompletableFuture<Response> get(String url) {
    HttpClient client = vertx.createHttpClient();

    HttpClientRequest request = client.getAbs(url);

    final CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    request.exceptionHandler(getCompleted::completeExceptionally);

    request.handler(response ->
      response.bodyHandler(buffer -> getCompleted.complete(
        new Response(response.statusCode(),
          buffer.getString(0, buffer.length())))));

    request.putHeader("X-Okapi-Tenant", TENANT_ID);
    request.putHeader("X-Okapi-Token", TOKEN);
    request.putHeader("X-Okapi-User-Id", USER_ID);
    request.putHeader("Accept", "application/json,text/plain");

    request.end();

    return getCompleted;
  }

  private CompletableFuture<Response> post(String url, String jsonContent) {
    HttpClient client = vertx.createHttpClient();

    HttpClientRequest request = client.postAbs(url);
    Buffer requestBuffer = Buffer.buffer(jsonContent);

    final CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    request.exceptionHandler(getCompleted::completeExceptionally);

    request.handler(response ->
      response.bodyHandler(responseBuffer -> {
        getCompleted.complete(new Response(
          response.statusCode(),
          responseBuffer.getString(0, responseBuffer.length())));
      }));

    request.putHeader("X-Okapi-Tenant", TENANT_ID);
    request.putHeader("X-Okapi-Token", TOKEN);
    request.putHeader("X-Okapi-User-Id", USER_ID);
    request.putHeader("Content-type", "application/json");
    request.putHeader("Accept", "application/json,text/plain");

    request.end(requestBuffer);

    return getCompleted;
  }

  private class Response {
    private final Integer statusCode;
    private final String body;

    private Response(Integer statusCode, String body) {
      this.statusCode = statusCode;
      this.body = body;
    }

    Integer getStatusCode() {
      return statusCode;
    }

    String getBody() {
      return body;
    }
  }

  public static <T> CompletableFuture<Void> allOf(
    List<CompletableFuture<T>> allFutures) {

    return CompletableFuture.allOf(allFutures.toArray(new CompletableFuture<?>[] { }));
  }
}
