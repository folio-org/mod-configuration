package org.folio.rest;


import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.DecodeException;
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
import org.folio.support.CompletableFutureExtensions;
import org.folio.support.ConfigurationRecordExamples;
import org.folio.support.OkapiHttpClient;
import org.folio.support.Response;
import org.folio.support.builders.ConfigurationRecordBuilder;
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
  private static final Vertx vertx = Vertx.vertx();
  private static int port;
  private static TenantClient tClient = null;
  private static final OkapiHttpClient okapiHttpClient = new OkapiHttpClient(
    vertx, TENANT_ID, USER_ID, TOKEN);

  static {
    System.setProperty(LoggerFactory.LOGGER_DELEGATE_FACTORY_CLASS_NAME,
      "io.vertx.core.logging.Log4jLogDelegateFactory");
  }

  @BeforeClass
  public static void beforeAll(TestContext context) {
    oldLocale = Locale.getDefault();
    Locale.setDefault(Locale.US);

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
  public void canCreateTenantConfigurationRecord(TestContext testContext) {
    final Async async = testContext.async();

    JsonObject configRecord = ConfigurationRecordExamples.audioAlertsExample().create();

    final CompletableFuture<Response> postCompleted = okapiHttpClient.post(
      "http://localhost:" + port + "/configurations/entries",
      configRecord.encodePrettily());

    postCompleted.thenAccept(response -> {
      try {
        testContext.assertEquals(201, response.getStatusCode(),
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
  public void canCreateTenantConfigurationRecordWithoutCode(TestContext testContext) {
    final Async async = testContext.async();

    JsonObject configRecord = new ConfigurationRecordBuilder()
      .withModuleName("CHECKOUT")
      .withConfigName("other_settings")
      .withValue("{ \"audioAlertsEnabled\": \"true\" }")
      .create();

    final CompletableFuture<Response> postCompleted = okapiHttpClient.post(
      "http://localhost:" + port + "/configurations/entries",
      configRecord.encodePrettily());

    postCompleted.thenAccept(response -> {
      try {
        testContext.assertEquals(201, response.getStatusCode(),
          String.format("Unexpected status code: '%s': '%s'", response.getStatusCode(),
            response.getBody()));

        System.out.println(String.format("Create Response: '%s'", response.getBody()));

        JsonObject createdRecord = new JsonObject(response.getBody());

        testContext.assertEquals("CHECKOUT", createdRecord.getString("module"));
        testContext.assertEquals("other_settings", createdRecord.getString("configName"));
        testContext.assertFalse(createdRecord.containsKey("code"));
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
  public void canCreateUserConfigurationRecord(TestContext testContext) {
    final Async async = testContext.async();

    final UUID userId = UUID.randomUUID();

    JsonObject configRecord = ConfigurationRecordExamples.audioAlertsExample()
      .forUser(userId)
      .create();

    final CompletableFuture<Response> postCompleted = okapiHttpClient.post(
      "http://localhost:" + port + "/configurations/entries",
      configRecord.encodePrettily());

    postCompleted.thenAccept(response -> {
      try {
        testContext.assertEquals(201, response.getStatusCode(),
          String.format("Unexpected status code: '%s': '%s'", response.getStatusCode(),
            response.getBody()));

        System.out.println(String.format("Create Response: '%s'", response.getBody()));

        JsonObject createdRecord = new JsonObject(response.getBody());

        testContext.assertEquals("CHECKOUT", createdRecord.getString("module"));
        testContext.assertEquals("other_settings", createdRecord.getString("configName"));
        testContext.assertEquals("audioAlertsEnabled", createdRecord.getString("code"));
        testContext.assertEquals("true", createdRecord.getString("value"));
        testContext.assertEquals(userId.toString(), createdRecord.getString("userId"));
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
  public void canCreateUserConfigurationRecordWithoutCode(TestContext testContext) {
    final Async async = testContext.async();

    final UUID userId = UUID.randomUUID();

    JsonObject configRecord = new ConfigurationRecordBuilder()
      .withModuleName("CHECKOUT")
      .withConfigName("other_settings")
      .withNoCode()
      .withValue("some value")
      .forUser(userId)
      .create();

    final CompletableFuture<Response> postCompleted = okapiHttpClient.post(
      "http://localhost:" + port + "/configurations/entries",
      configRecord.encodePrettily());

    postCompleted.thenAccept(response -> {
      try {
        testContext.assertEquals(201, response.getStatusCode(),
          String.format("Unexpected status code: '%s': '%s'", response.getStatusCode(),
            response.getBody()));

        System.out.println(String.format("Create Response: '%s'", response.getBody()));

        JsonObject createdRecord = new JsonObject(response.getBody());

        testContext.assertEquals("CHECKOUT", createdRecord.getString("module"));
        testContext.assertEquals("other_settings", createdRecord.getString("configName"));
        testContext.assertFalse(createdRecord.containsKey("code"), "Should not have a code");
        testContext.assertEquals("some value", createdRecord.getString("value"));
        testContext.assertEquals(userId.toString(), createdRecord.getString("userId"));
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
  public void canCreateMultipleConfigurationRecordsWithDifferentConfigNameWithoutCode(
    TestContext testContext) {

    final Async async = testContext.async();

    JsonObject firstConfigRecord = new ConfigurationRecordBuilder()
      .withModuleName("CHECKOUT")
      .withConfigName("main_settings")
      .withValue("some value")
      .create();

    final CompletableFuture<Response> firstRecordCompleted = okapiHttpClient.post(
      "http://localhost:" + port + "/configurations/entries",
      firstConfigRecord.encodePrettily());

    JsonObject secondConfigRecord = new ConfigurationRecordBuilder()
      .withModuleName("CHECKOUT")
      .withConfigName("other_settings")
      .withValue("some other value")
      .create();

    final CompletableFuture<Response> secondRecordCompleted = okapiHttpClient.post(
      "http://localhost:" + port + "/configurations/entries",
      secondConfigRecord.encodePrettily());

    List<CompletableFuture<Response>> allRecordsFutures = new ArrayList<>();
    allRecordsFutures.add(firstRecordCompleted);
    allRecordsFutures.add(secondRecordCompleted);

    CompletableFuture<Void> allRecordsCompleted = CompletableFutureExtensions.allOf(allRecordsFutures);

    allRecordsCompleted.thenAccept(v ->
      checkAllRecordsCreated(allRecordsFutures, testContext, async));
  }

  @Test
  public void canCreateMultipleConfigurationRecordsWithDifferentModuleWithoutCode(
    TestContext testContext) {
    final Async async = testContext.async();

    JsonObject firstConfigRecord = new ConfigurationRecordBuilder()
      .withModuleName("CHECKOUT")
      .withConfigName("main_settings")
      .withValue("some value")
      .create();

    final CompletableFuture<Response> firstRecordCompleted = okapiHttpClient.post(
      "http://localhost:" + port + "/configurations/entries",
      firstConfigRecord.encodePrettily());

    JsonObject secondConfigRecord = new ConfigurationRecordBuilder()
      .withModuleName("RENEWAL")
      .withConfigName("main_settings")
      .withValue("some other value")
      .create();

    final CompletableFuture<Response> secondRecordCompleted = okapiHttpClient.post(
      "http://localhost:" + port + "/configurations/entries",
      secondConfigRecord.encodePrettily());

    List<CompletableFuture<Response>> allRecordsFutures = new ArrayList<>();
    allRecordsFutures.add(firstRecordCompleted);
    allRecordsFutures.add(secondRecordCompleted);

    CompletableFuture<Void> allRecordsCompleted = CompletableFutureExtensions.allOf(allRecordsFutures);

    allRecordsCompleted.thenAccept(v ->
      checkAllRecordsCreated(allRecordsFutures, testContext, async));
  }

  @Test
  public void canCreateMultipleConfigurationRecordsWithDifferentConfigName(
    TestContext testContext) {
    final Async async = testContext.async();

    JsonObject firstConfigRecord = new ConfigurationRecordBuilder()
      .withModuleName("CHECKOUT")
      .withConfigName("main_settings")
      .withCode("first_setting")
      .withValue("some value")
      .create();

    final CompletableFuture<Response> firstRecordCompleted = okapiHttpClient.post(
      "http://localhost:" + port + "/configurations/entries",
      firstConfigRecord.encodePrettily());

    JsonObject secondConfigRecord = new ConfigurationRecordBuilder()
      .withModuleName("CHECKOUT")
      .withConfigName("main_settings")
      .withCode("second_setting")
      .withValue("some other value")
      .create();

    final CompletableFuture<Response> secondRecordCompleted = okapiHttpClient.post(
      "http://localhost:" + port + "/configurations/entries",
      secondConfigRecord.encodePrettily());

    List<CompletableFuture<Response>> allRecordsFutures = new ArrayList<>();
    allRecordsFutures.add(firstRecordCompleted);
    allRecordsFutures.add(secondRecordCompleted);

    CompletableFuture<Void> allRecordsCompleted = CompletableFutureExtensions.allOf(allRecordsFutures);

    allRecordsCompleted.thenAccept(v ->
      checkAllRecordsCreated(allRecordsFutures, testContext, async));
  }

  @Test
  public void canCreateMultipleConfigurationRecordsWithDifferentModuleName(
    TestContext testContext) {
    final Async async = testContext.async();

    JsonObject firstConfigRecord = new ConfigurationRecordBuilder()
      .withModuleName("CHECKOUT")
      .withConfigName("main_settings")
      .withCode("first_setting")
      .withValue("some value")
      .create();

    final CompletableFuture<Response> firstRecordCompleted = okapiHttpClient.post(
      "http://localhost:" + port + "/configurations/entries",
      firstConfigRecord.encodePrettily());

    JsonObject secondConfigRecord = new ConfigurationRecordBuilder()
      .withModuleName("RENEWAL")
      .withConfigName("main_settings")
      .withCode("first_setting")
      .withValue("some other value")
      .create();

    final CompletableFuture<Response> secondRecordCompleted = okapiHttpClient.post(
      "http://localhost:" + port + "/configurations/entries",
      secondConfigRecord.encodePrettily());

    List<CompletableFuture<Response>> allRecordsFutures = new ArrayList<>();
    allRecordsFutures.add(firstRecordCompleted);
    allRecordsFutures.add(secondRecordCompleted);

    CompletableFuture<Void> allRecordsCompleted = CompletableFutureExtensions.allOf(allRecordsFutures);

    allRecordsCompleted.thenAccept(v ->
      checkAllRecordsCreated(allRecordsFutures, testContext, async));
  }

  @Test
  public void cannotCreateMultipleRecordsWithSameModuleConfigAndCode(TestContext testContext)
    throws InterruptedException,
    ExecutionException,
    TimeoutException {

    JsonObject firstConfigRecord = new ConfigurationRecordBuilder()
      .withModuleName("CHECKOUT")
      .withConfigName("other_settings")
      .withCode("audioAlertsEnabled")
      .withValue("some value")
      .create();

    final CompletableFuture<Response> firstRecordCreated = okapiHttpClient.post(
      "http://localhost:" + port + "/configurations/entries",
      firstConfigRecord.encodePrettily());

    //Make sure the first record is created before the second
    final Response firstRecordResponse = firstRecordCreated.get(5, TimeUnit.SECONDS);

    JsonObject secondConfigRecord = new ConfigurationRecordBuilder()
      .withModuleName("CHECKOUT")
      .withConfigName("other_settings")
      .withCode("audioAlertsEnabled")
      .withValue("some other value")
      .create();

    final CompletableFuture<Response> secondRecordCreated = okapiHttpClient.post(
      "http://localhost:" + port + "/configurations/entries",
      secondConfigRecord.encodePrettily());

    final Response secondRecordResponse = secondRecordCreated.get(5, TimeUnit.SECONDS);

    testContext.assertEquals(201, firstRecordResponse.getStatusCode(),
      String.format("Unexpected status code: '%s': '%s'", firstRecordResponse.getStatusCode(),
        firstRecordResponse.getBody()));

    testContext.assertEquals(422, secondRecordResponse.getStatusCode(),
      String.format("Unexpected status code: '%s': '%s'", secondRecordResponse.getStatusCode(),
        secondRecordResponse.getBody()));
  }

  @Test
  public void cannotCreateMultipleRecordsWithSameModuleConfigWithoutCode(TestContext testContext)
    throws InterruptedException,
    ExecutionException,
    TimeoutException {

    JsonObject firstConfigRecord = new ConfigurationRecordBuilder()
      .withModuleName("CHECKOUT")
      .withConfigName("other_settings")
      .withValue("some value")
      .create();

    final CompletableFuture<Response> firstRecordCreated = okapiHttpClient.post(
      "http://localhost:" + port + "/configurations/entries",
      firstConfigRecord.encodePrettily());

    //Make sure the first record is created before the second
    final Response firstRecordResponse = firstRecordCreated.get(5, TimeUnit.SECONDS);

    JsonObject secondConfigRecord = new ConfigurationRecordBuilder()
      .withModuleName("CHECKOUT")
      .withConfigName("other_settings")
      .withValue("some other value")
      .create();

    final CompletableFuture<Response> secondRecordCreated = okapiHttpClient.post(
      "http://localhost:" + port + "/configurations/entries",
      secondConfigRecord.encodePrettily());

    final Response secondRecordResponse = secondRecordCreated.get(5, TimeUnit.SECONDS);

    testContext.assertEquals(201, firstRecordResponse.getStatusCode(),
      String.format("Unexpected status code: '%s': '%s'", firstRecordResponse.getStatusCode(),
        firstRecordResponse.getBody()));

    testContext.assertEquals(422, secondRecordResponse.getStatusCode(),
      String.format("Unexpected status code: '%s': '%s'", secondRecordResponse.getStatusCode(),
        secondRecordResponse.getBody()));
  }

  @Test
  public void canGetConfigurationRecords(TestContext testContext) {
    final Async async = testContext.async();

    final ArrayList<CompletableFuture<Response>> allCreated = new ArrayList<>();

    JsonObject firstConfigRecord = ConfigurationRecordExamples.audioAlertsExample().create();

    allCreated.add(okapiHttpClient.post(
      "http://localhost:" + port + "/configurations/entries",
      firstConfigRecord.encodePrettily()));

    JsonObject secondConfigRecord = ConfigurationRecordExamples.timeOutDurationExample().create();

    allCreated.add(okapiHttpClient.post(
      "http://localhost:" + port + "/configurations/entries",
      secondConfigRecord.encodePrettily()));

    CompletableFutureExtensions.allOf(allCreated).thenComposeAsync(v ->
      //Must filter to only check out module entries due to default locale records
      okapiHttpClient.get("http://localhost:" + port + "/configurations/entries?query=module==CHECKOUT"))
    .thenAccept(response -> {
      try {
        testContext.assertEquals(200, response.getStatusCode(),
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
  public void canSortConfigurationRecordsByCreatedDate(TestContext testContext)
    throws UnsupportedEncodingException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    final Async async = testContext.async();

    JsonObject firstConfigRecord = ConfigurationRecordExamples.audioAlertsExample().create();

    final CompletableFuture<Response> firstRecordCreated = okapiHttpClient.post(
      "http://localhost:" + port + "/configurations/entries",
      firstConfigRecord.encodePrettily());

    //Make sure the first record is created before the second
    firstRecordCreated.get(5, TimeUnit.SECONDS);

    JsonObject secondConfigRecord = ConfigurationRecordExamples.timeOutDurationExample().create();

    final CompletableFuture<Response> secondRecordCreated = okapiHttpClient.post(
      "http://localhost:" + port + "/configurations/entries",
      secondConfigRecord.encodePrettily());

    secondRecordCreated.get(5, TimeUnit.SECONDS);

    String encodedQuery = URLEncoder.encode("module==CHECKOUT sortBy metadata.createdDate/sort.descending",
      StandardCharsets.UTF_8.name());

    //Must filter to only check out module entries due to default locale records
    okapiHttpClient.get("http://localhost:" + port + "/configurations/entries" + "?query=" + encodedQuery)
      .thenAccept(response -> {
        try {
          testContext.assertEquals(200, response.getStatusCode(),
            String.format("Unexpected status code: '%s': '%s'", response.getStatusCode(),
              response.getBody()));

          JsonObject wrappedRecords = new JsonObject(response.getBody());

          testContext.assertEquals(2, wrappedRecords.getInteger("totalRecords"));

          final JsonArray records = wrappedRecords.getJsonArray("configs");

          testContext.assertEquals("checkoutTimeoutDuration",
            records.getJsonObject(0).getString("code"));

          testContext.assertEquals("audioAlertsEnabled",
            records.getJsonObject(1).getString("code"));
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
      String sample = getFile("kv_configuration.sample");

      ConfigurationRecordBuilder baselineFromSample = ConfigurationRecordBuilder.from(sample);

      mutateURLs("http://localhost:" + port + "/configurations/entries", context, HttpMethod.POST,
        baselineFromSample.create().encodePrettily(), "application/json", 201);

      //save config entry with value being a base64 encoded file
      String bytes = Base64.getEncoder().encodeToString(getFile("Sample.drl").getBytes());

      ConfigurationRecordBuilder encodedValueExample = baselineFromSample
        .withCode("encoded_example")
        .withValue(bytes);

      mutateURLs("http://localhost:" + port + "/configurations/entries", context, HttpMethod.POST,
        encodedValueExample.create().encodePrettily(), "application/json", 201);

      ConfigurationRecordBuilder disabledExample = baselineFromSample
        .withCode("enabled_example")
        .withValue(bytes)
        .disabled();

      mutateURLs("http://localhost:" + port + "/configurations/entries", context, HttpMethod.POST,
        disabledExample.create().encodePrettily(), "application/json", 201);

      //This looks to be exactly the same use case
//      mutateURLs("http://localhost:" + port + "/configurations/entries", context, HttpMethod.POST,
//        new ObjectMapper().writeValueAsString(conf), "application/json", 201);

      //attempt to delete invalud id (not uuid)
      mutateURLs("http://localhost:" + port + "/configurations/entries/123456", context, HttpMethod.DELETE,
        "", "application/json", 404);

      mutateURLs("http://localhost:" + port + "/admin/kill_query?pid=11", context, HttpMethod.DELETE,
        "", "application/json", 404);

      //check read only
      Config conf2 =  new ObjectMapper().readValue(sample, Config.class);

      conf2.setCode("change_metadata_example");

      Metadata md = new Metadata();
      md.setCreatedByUserId("123456");
      conf2.setMetadata(md);

      mutateURLs("http://localhost:" + port + "/configurations/entries", context, HttpMethod.POST,
        new ObjectMapper().writeValueAsString(conf2), "application/json", 422);

      md.setCreatedByUserId("2b94c631-fca9-a892-c730-03ee529ffe2a");
      md.setCreatedDate(new Date());
      md.setUpdatedDate(new Date());

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
      urlsToCheck.forEach(line -> {
        Async async = context.async();

        String[] urlInfo = line.split(" , ");
        final String url = urlInfo[1].trim().replaceFirst("<port>", port + "");
        final Integer expectedStatusCode = Integer.parseInt(urlInfo[3]);

        final Integer expectedRecordCount = urlInfo.length == 5
          ? Integer.parseInt(urlInfo[4])
          : null;

        final CompletableFuture<Response> responded = okapiHttpClient.get(url);

        try {
          Response response = responded.get(5, TimeUnit.SECONDS);

          context.assertEquals(expectedStatusCode, response.getStatusCode(),
            String.format("Unexpected status code from '%s': '%s'", url, response.getBody()));

          if(expectedRecordCount != null && expectedRecordCount > 0) {
            try {
              JsonObject wrappedRecords = new JsonObject(response.getBody());

              context.assertEquals(expectedRecordCount, wrappedRecords.getInteger("totalRecords"),
                String.format("Unexpected record count for '%s': '%s'", url, response.getBody()));
            }
            catch(DecodeException e) {
              context.fail(String.format("Could not decide '%s' - %s", response.getBody(), e.getMessage()));
            }
          }
        }
        catch(Exception e) {
          context.fail(e);
        }
        finally {
          async.complete();
        }
        });
    } catch (Throwable e) {
      e.printStackTrace();
      context.fail(e);
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

  private void checkAllRecordsCreated(
    Iterable<CompletableFuture<Response>> allRecordsFutures,
    TestContext testContext,
    Async async) {

    try {
      for (CompletableFuture<Response> future : allRecordsFutures) {
        Response response = future.get();

        testContext.assertEquals(201, response.getStatusCode(),
          String.format("Unexpected status code: '%s': '%s'", response.getStatusCode(),
            response.getBody()));
      }
    }
    catch(Exception e) {
      testContext.fail(e);
    }
    finally {
      async.complete();
    }
  }
}
