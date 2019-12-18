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
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.apache.commons.io.IOUtils;
import org.folio.rest.client.TenantClient;
import org.folio.rest.jaxrs.model.Config;
import org.folio.rest.jaxrs.model.Metadata;
import org.folio.rest.jaxrs.model.Parameter;
import org.folio.rest.jaxrs.model.TenantAttributes;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.security.AES;
import org.folio.rest.tools.PomReader;
import org.folio.rest.tools.utils.NetworkUtils;
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
import java.lang.invoke.MethodHandles;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.folio.support.CompletableFutureExtensions.allOf;

/**
 * This is our JUnit test for our verticle. The test uses vertx-unit, so we declare a custom runner.
 */
@RunWith(VertxUnitRunner.class)
public class RestVerticleTest {
  private static final String UNEXPECTED_STATUS_CODE = "Unexpected status code: '%s': '%s'";

  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private static final String SECRET_KEY = "b2+S+X4F/NFys/0jMaEG1A";
  private static final String TENANT_ID = "harvard";
  private static final String USER_ID = "79ff2a8b-d9c3-5b39-ad4a-0a84025ab085";

  private static Locale oldLocale;
  private static final Vertx vertx = Vertx.vertx();
  private static int port;
  private static TenantClient tClient = null;
  private static final OkapiHttpClient okapiHttpClient = new OkapiHttpClient(
    vertx, TENANT_ID, USER_ID);

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
      context.fail(e);
      return;
    }

    Async async = context.async();

    port = NetworkUtils.nextFreePort();

    tClient = new TenantClient("http://localhost:"+Integer.toString(port), TENANT_ID, null);

    DeploymentOptions options = new DeploymentOptions().setConfig(
      new JsonObject().put("http.port", port)).setWorker(true);

    vertx.deployVerticle(RestVerticle.class.getName(), options, context.asyncAssertSuccess(id -> {
      try {
        TenantAttributes ta = new TenantAttributes();
        ta.setModuleTo("mod-configuration-1.0.0");
        List<Parameter> parameters = new LinkedList<>();
        parameters.add(new Parameter().withKey("loadSample").withValue("true"));
        ta.setParameters(parameters);
        tClient.postTenant(ta, res2 -> {
          context.assertEquals(201, res2.statusCode(), "postTenant: " + res2.statusMessage());
          async.complete();
        });
      } catch (Exception e) {
        context.fail(e);
        async.complete();
      }
    }));
  }

  @AfterClass
  public static void afterAll(TestContext context) {
    Async async = context.async();
    tClient.deleteTenant(reply -> reply.bodyHandler(body -> {
      log.debug(body.toString());
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
  public void verifySampleDataLoaded(TestContext testContext) {
    final Async async = testContext.async();
    okapiHttpClient.get("http://localhost:" + port + "/configurations/entries?query=module==SETTINGS")
  .thenAccept(response -> {
    try {
      testContext.assertEquals(200, response.getStatusCode(),
        String.format(UNEXPECTED_STATUS_CODE, response.getStatusCode(),
          response.getBody()));
      JsonObject wrappedRecords = new JsonObject(response.getBody());
      testContext.assertEquals(10, wrappedRecords.getInteger("totalRecords"));
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
  public void verifySampleDataCurrencyCodeDk(TestContext testContext) {
    final String uuid = "b873eb5a-7a50-488a-9624-d4fbc4daad51";
    final Async async = testContext.async();
    okapiHttpClient.get("http://localhost:" + port + "/configurations/entries/" + uuid)
      .thenAccept(response -> {
        try {
          testContext.assertEquals(200, response.getStatusCode(),
            String.format(UNEXPECTED_STATUS_CODE, response.getStatusCode(),
              response.getBody()));
          JsonObject wrappedRecords = new JsonObject(response.getBody());
          testContext.assertEquals(uuid, wrappedRecords.getString("id"));
        } catch (Exception e) {
          testContext.fail(e);
        } finally {
          async.complete();
        }
      });
  }

  /**
   * Test upgrade (2nd Tenant POST)
   * @param testContext
   */
  @Test
  public void upgradeTenantWithSampleDataLoaded(TestContext testContext) {
    final Async async = testContext.async();

    String moduleId = String.format("%s-%s", PomReader.INSTANCE.getModuleName(), PomReader.INSTANCE.getVersion());

    try {
      TenantAttributes ta = new TenantAttributes();
      ta.setModuleTo(moduleId);
      ta.setModuleFrom("mod-configuration-1.0.0");
      List<Parameter> parameters = new LinkedList<>();
      parameters.add(new Parameter().withKey("loadSample").withValue("true"));
      ta.setParameters(parameters);
      tClient.postTenant(ta, res2 -> {
        testContext.assertEquals(201, res2.statusCode(), "postTenant: " + res2.statusMessage());
        async.complete();
      });
    } catch (Exception e) {
      testContext.fail(e);
    }

  }

  @Test
  public void canCreateTenantConfigurationRecord(TestContext testContext) {
    final Async async = testContext.async();

    JsonObject configRecord = ConfigurationRecordExamples.audioAlertsExample().create();

    final CompletableFuture<Response> postCompleted = createConfigRecord(configRecord);

    postCompleted.thenAccept(response -> {
      try {
        testContext.assertEquals(201, response.getStatusCode(),
          String.format(UNEXPECTED_STATUS_CODE, response.getStatusCode(),
            response.getBody()));

        log.debug(String.format("Create Response: '%s'", response.getBody()));

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

    final CompletableFuture<Response> postCompleted = createConfigRecord(configRecord);

    postCompleted.thenAccept(response -> {
      try {
        testContext.assertEquals(201, response.getStatusCode(),
          String.format(UNEXPECTED_STATUS_CODE, response.getStatusCode(),
            response.getBody()));

        log.debug(String.format("Create Response: '%s'", response.getBody()));

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

    final CompletableFuture<Response> postCompleted = createConfigRecord(configRecord);

    postCompleted.thenAccept(response -> {
      try {
        testContext.assertEquals(201, response.getStatusCode(),
          String.format(UNEXPECTED_STATUS_CODE, response.getStatusCode(),
            response.getBody()));

        log.debug(String.format("Create Response: '%s'", response.getBody()));

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

    final CompletableFuture<Response> postCompleted = createConfigRecord(configRecord);

    postCompleted.thenAccept(response -> {
      try {
        testContext.assertEquals(201, response.getStatusCode(),
          String.format(UNEXPECTED_STATUS_CODE, response.getStatusCode(),
            response.getBody()));

        log.debug(String.format("Create Response: '%s'", response.getBody()));

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

  //Only a single example, rather than replicating all of the examples used for POST
  @Test
  public void canReplaceConfigurationRecordUsingPut(TestContext testContext)
    throws InterruptedException,
    ExecutionException,
    TimeoutException {

    JsonObject configRecord = ConfigurationRecordExamples
      .audioAlertsExample()
      .create();

    final CompletableFuture<Response> postCompleted = createConfigRecord(configRecord);

    final Response response = postCompleted.get(5, TimeUnit.SECONDS);

    final JsonObject createdRecord = response.getBodyAsJson();
    String id = createdRecord.getString("id");

    JsonObject putRequest = ConfigurationRecordBuilder.from(createdRecord)
      .withModuleName("a_new_module")
      .withConfigName("a_new_config_name")
      .withCode("a_new_code")
      .withValue("a_new_value")
      .create();

    final CompletableFuture<Response> putCompleted = okapiHttpClient.put(
      "http://localhost:" + port + "/configurations/entries/" + id,
      putRequest.encodePrettily());

    final Response putResponse = putCompleted.get(5, TimeUnit.SECONDS);

    testContext.assertEquals(204, putResponse.getStatusCode(),
      String.format(UNEXPECTED_STATUS_CODE, putResponse.getStatusCode(),
        putResponse.getBody()));

    final Response getResponse = okapiHttpClient.get(
      "http://localhost:" + port + "/configurations/entries/" + id)
      .get(5, TimeUnit.SECONDS);

    JsonObject updatedRecord = getResponse.getBodyAsJson();

    testContext.assertEquals("a_new_module", updatedRecord.getString("module"));
    testContext.assertEquals("a_new_config_name", updatedRecord.getString("configName"));
    testContext.assertEquals("a_new_code", updatedRecord.getString("code"));
    testContext.assertEquals("a_new_value", updatedRecord.getString("value"));

    testContext.assertTrue(updatedRecord.containsKey("metadata"),
      String.format("Should contain change metadata property: '%s'",
        updatedRecord.encodePrettily()));

    final JsonObject changeMetadata = updatedRecord.getJsonObject("metadata");

    testContext.assertTrue(changeMetadata.containsKey("createdDate"),
      String.format("Should contain created date property: '%s'", changeMetadata));

    testContext.assertTrue(changeMetadata.containsKey("createdByUserId"),
      String.format("Should contain created by property: '%s'", changeMetadata));

    testContext.assertTrue(changeMetadata.containsKey("updatedDate"),
      String.format("Should contain updated date property: '%s'", changeMetadata));

    testContext.assertTrue(changeMetadata.containsKey("updatedByUserId"),
      String.format("Should contain updated by property: '%s'", changeMetadata));
  }

  @Test
  public void canDisableConfigurationRecord(TestContext testContext)
    throws InterruptedException,
    ExecutionException,
    TimeoutException {

    JsonObject configRecord = ConfigurationRecordExamples
      .audioAlertsExample()
      .create();

    final CompletableFuture<Response> postCompleted = createConfigRecord(configRecord);

    final Response response = postCompleted.get(5, TimeUnit.SECONDS);

    final JsonObject createdRecord = response.getBodyAsJson();
    String id = createdRecord.getString("id");

    JsonObject putRequest = ConfigurationRecordBuilder.from(createdRecord)
      .disabled()
      .create();

    final CompletableFuture<Response> putCompleted = okapiHttpClient.put(
      "http://localhost:" + port + "/configurations/entries/" + id,
      putRequest.encodePrettily());

    final Response putResponse = putCompleted.get(5, TimeUnit.SECONDS);

    testContext.assertEquals(204, putResponse.getStatusCode(),
      String.format(UNEXPECTED_STATUS_CODE, putResponse.getStatusCode(),
        putResponse.getBody()));

    final Response getResponse = okapiHttpClient.get(
      "http://localhost:" + port + "/configurations/entries/" + id)
      .get(5, TimeUnit.SECONDS);

    JsonObject updatedRecord = getResponse.getBodyAsJson();

    testContext.assertTrue(updatedRecord.containsKey("enabled"),
      "Should have enabled property");

    testContext.assertFalse(updatedRecord.getBoolean("enabled"),
      "Should be disabled");
  }

  @Test
  public void configurationRecordIsEnabledByDefaultWhenReplaced(TestContext testContext)
    throws InterruptedException,
    ExecutionException,
    TimeoutException {

    JsonObject configRecord = ConfigurationRecordExamples
      .audioAlertsExample()
      .create();

    final CompletableFuture<Response> postCompleted = createConfigRecord(configRecord);

    final Response response = postCompleted.get(5, TimeUnit.SECONDS);

    final JsonObject createdRecord = response.getBodyAsJson();
    String id = createdRecord.getString("id");

    JsonObject putRequest = ConfigurationRecordBuilder.from(createdRecord)
      .withNoEnabled()
      .create();

    final CompletableFuture<Response> putCompleted = okapiHttpClient.put(
      "http://localhost:" + port + "/configurations/entries/" + id,
      putRequest.encodePrettily());

    final Response putResponse = putCompleted.get(5, TimeUnit.SECONDS);

    testContext.assertEquals(204, putResponse.getStatusCode(),
      String.format(UNEXPECTED_STATUS_CODE, putResponse.getStatusCode(),
        putResponse.getBody()));

    final Response getResponse = okapiHttpClient.get(
      "http://localhost:" + port + "/configurations/entries/" + id)
      .get(5, TimeUnit.SECONDS);

    JsonObject updatedRecord = getResponse.getBodyAsJson();

    testContext.assertTrue(updatedRecord.containsKey("enabled"),
      "Should have enabled property");

    testContext.assertTrue(updatedRecord.getBoolean("enabled"),
      "Should be enabled");
  }

  @Test
  public void canCreateTenantAndGroupConfigurationRecordsForSameModuleConfigNameAndCode(
    TestContext testContext) {

    final Async async = testContext.async();

    List<CompletableFuture<Response>> allRecordsFutures = new ArrayList<>();

    final ConfigurationRecordBuilder baselineSetting = new ConfigurationRecordBuilder()
      .withModuleName("CHECKOUT")
      .withConfigName("main_settings")
      .withCode("example_setting")
      .withValue("some value");

    JsonObject tenantConfigRecord = baselineSetting
      .forNoUser()
      .create();

    allRecordsFutures.add(createConfigRecord(tenantConfigRecord));

    final UUID firstUserId = UUID.randomUUID();

    JsonObject firstUserConfigRecord = baselineSetting
      .forUser(firstUserId)
      .withValue("another value")
      .create();

    allRecordsFutures.add(createConfigRecord(firstUserConfigRecord));

    final UUID secondUserId = UUID.randomUUID();

    JsonObject secondUserConfigRecord = baselineSetting
      .forUser(secondUserId)
      .withValue("a different value")
      .create();

    allRecordsFutures.add(createConfigRecord(secondUserConfigRecord));

    CompletableFuture<Void> allRecordsCompleted = allOf(allRecordsFutures);

    allRecordsCompleted.thenAccept(v ->
      checkAllRecordsCreated(allRecordsFutures, testContext, async));
  }

  @Test
  public void canCreateTenantAndGroupConfigurationRecordsForSameModuleConfigNameAndNoCode(
    TestContext testContext) {

    final Async async = testContext.async();

    List<CompletableFuture<Response>> allRecordsFutures = new ArrayList<>();

    final ConfigurationRecordBuilder baselineSetting = new ConfigurationRecordBuilder()
      .withModuleName("CHECKOUT")
      .withConfigName("main_settings")
      .withNoCode()
      .withValue("some value");

    JsonObject tenantConfigRecord = baselineSetting
      .forNoUser()
      .create();

    allRecordsFutures.add(createConfigRecord(tenantConfigRecord));

    final UUID firstUserId = UUID.randomUUID();

    JsonObject firstUserConfigRecord = baselineSetting
      .forUser(firstUserId)
      .withValue("another value")
      .create();

    allRecordsFutures.add(createConfigRecord(firstUserConfigRecord));

    final UUID secondUserId = UUID.randomUUID();

    JsonObject secondUserConfigRecord = baselineSetting
      .forUser(secondUserId)
      .withValue("a different value")
      .create();

    allRecordsFutures.add(createConfigRecord(secondUserConfigRecord));

    CompletableFuture<Void> allRecordsCompleted = allOf(allRecordsFutures);

    allRecordsCompleted.thenAccept(v ->
      checkAllRecordsCreated(allRecordsFutures, testContext, async));
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

    final CompletableFuture<Response> firstRecordCompleted = createConfigRecord(firstConfigRecord);

    JsonObject secondConfigRecord = new ConfigurationRecordBuilder()
      .withModuleName("CHECKOUT")
      .withConfigName("other_settings")
      .withValue("some other value")
      .create();

    final CompletableFuture<Response> secondRecordCompleted = createConfigRecord(secondConfigRecord);

    List<CompletableFuture<Response>> allRecordsFutures = new ArrayList<>();
    allRecordsFutures.add(firstRecordCompleted);
    allRecordsFutures.add(secondRecordCompleted);

    CompletableFuture<Void> allRecordsCompleted = allOf(allRecordsFutures);

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

    final CompletableFuture<Response> firstRecordCompleted = createConfigRecord(firstConfigRecord);

    JsonObject secondConfigRecord = new ConfigurationRecordBuilder()
      .withModuleName("RENEWAL")
      .withConfigName("main_settings")
      .withValue("some other value")
      .create();

    final CompletableFuture<Response> secondRecordCompleted = createConfigRecord(secondConfigRecord);

    List<CompletableFuture<Response>> allRecordsFutures = new ArrayList<>();
    allRecordsFutures.add(firstRecordCompleted);
    allRecordsFutures.add(secondRecordCompleted);

    CompletableFuture<Void> allRecordsCompleted = allOf(allRecordsFutures);

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

    final CompletableFuture<Response> firstRecordCompleted = createConfigRecord(firstConfigRecord);

    JsonObject secondConfigRecord = new ConfigurationRecordBuilder()
      .withModuleName("CHECKOUT")
      .withConfigName("main_settings")
      .withCode("second_setting")
      .withValue("some other value")
      .create();

    final CompletableFuture<Response> secondRecordCompleted = createConfigRecord(secondConfigRecord);

    List<CompletableFuture<Response>> allRecordsFutures = new ArrayList<>();
    allRecordsFutures.add(firstRecordCompleted);
    allRecordsFutures.add(secondRecordCompleted);

    CompletableFuture<Void> allRecordsCompleted = allOf(allRecordsFutures);

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

    final CompletableFuture<Response> firstRecordCompleted = createConfigRecord(firstConfigRecord);

    JsonObject secondConfigRecord = new ConfigurationRecordBuilder()
      .withModuleName("RENEWAL")
      .withConfigName("main_settings")
      .withCode("first_setting")
      .withValue("some other value")
      .create();

    final CompletableFuture<Response> secondRecordCompleted = createConfigRecord(secondConfigRecord);

    List<CompletableFuture<Response>> allRecordsFutures = new ArrayList<>();
    allRecordsFutures.add(firstRecordCompleted);
    allRecordsFutures.add(secondRecordCompleted);

    CompletableFuture<Void> allRecordsCompleted = allOf(allRecordsFutures);

    allRecordsCompleted.thenAccept(v ->
      checkAllRecordsCreated(allRecordsFutures, testContext, async));
  }

  @Test
  public void createdConfigurationRecordIsEnabledByDefault(TestContext testContext)
    throws InterruptedException,
    ExecutionException,
    TimeoutException {

    JsonObject configRecord = new ConfigurationRecordBuilder()
      .withModuleName("some_module")
      .withConfigName("other_settings")
      .withCode("some_code")
      .withValue("some value")
      .withNoEnabled()
      .create();

    final CompletableFuture<Response> postCompleted = createConfigRecord(configRecord);

    final Response response = postCompleted.get(5, TimeUnit.SECONDS);

    testContext.assertEquals(201, response.getStatusCode(),
      String.format(UNEXPECTED_STATUS_CODE, response.getStatusCode(),
        response.getBody()));

    log.debug(String.format("Create Response: '%s'", response.getBody()));

    JsonObject createdRecord = new JsonObject(response.getBody());

    testContext.assertTrue(createdRecord.containsKey("enabled"),
      "Should have enabled property");

    testContext.assertEquals(true, createdRecord.getBoolean("enabled"),
      "Should be enabled");
  }

  @Test
  public void canCreatedDisabledConfigurationRecord(TestContext testContext)
    throws InterruptedException,
    ExecutionException,
    TimeoutException {

    JsonObject configRecord = new ConfigurationRecordBuilder()
      .withModuleName("some_module")
      .withConfigName("other_settings")
      .withCode("some_code")
      .withValue("some value")
      .disabled()
      .create();

    final CompletableFuture<Response> postCompleted = createConfigRecord(configRecord);

    final Response response = postCompleted.get(5, TimeUnit.SECONDS);

    testContext.assertEquals(201, response.getStatusCode(),
      String.format(UNEXPECTED_STATUS_CODE, response.getStatusCode(),
        response.getBody()));

    log.debug(String.format("Create Response: '%s'", response.getBody()));

    JsonObject createdRecord = new JsonObject(response.getBody());

    testContext.assertTrue(createdRecord.containsKey("enabled"),
      "Should have enabled property");

    testContext.assertEquals(false, createdRecord.getBoolean("enabled"));
  }

  @Test
  public void cannotCreateConfigurationRecordUsingPut(TestContext testContext) {
    final Async async = testContext.async();

    final UUID id = UUID.randomUUID();

    JsonObject configRecord = ConfigurationRecordExamples
      .audioAlertsExample()
      .withId(id)
      .create();

    final CompletableFuture<Response> putCompleted = okapiHttpClient.put(
      "http://localhost:" + port + "/configurations/entries/" + id.toString(),
      configRecord.encodePrettily());

    putCompleted.thenAccept(response -> {
      try {
        testContext.assertEquals(404, response.getStatusCode(),
          String.format(UNEXPECTED_STATUS_CODE, response.getStatusCode(),
            response.getBody()));
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
  public void cannotCreateMultipleTenantRecordsWithSameModuleConfigAndCode(
    TestContext testContext)
    throws InterruptedException,
    ExecutionException,
    TimeoutException {

    JsonObject firstConfigRecord = new ConfigurationRecordBuilder()
      .withModuleName("CHECKOUT")
      .withConfigName("other_settings")
      .withCode("audioAlertsEnabled")
      .withValue("some value")
      .create();

    final CompletableFuture<Response> firstRecordCreated = createConfigRecord(firstConfigRecord);

    //Make sure the first record is created before the second
    final Response firstRecordResponse = firstRecordCreated.get(5, TimeUnit.SECONDS);

    JsonObject secondConfigRecord = new ConfigurationRecordBuilder()
      .withModuleName("CHECKOUT")
      .withConfigName("other_settings")
      .withCode("audioAlertsEnabled")
      .withValue("some other value")
      .create();

    final CompletableFuture<Response> secondRecordCreated = createConfigRecord(secondConfigRecord);

    final Response secondRecordResponse = secondRecordCreated.get(5, TimeUnit.SECONDS);

    testContext.assertEquals(201, firstRecordResponse.getStatusCode(),
      String.format(UNEXPECTED_STATUS_CODE, firstRecordResponse.getStatusCode(),
        firstRecordResponse.getBody()));

    testContext.assertEquals(422, secondRecordResponse.getStatusCode(),
      String.format(UNEXPECTED_STATUS_CODE, secondRecordResponse.getStatusCode(),
        secondRecordResponse.getBody()));
  }

  @Test
  public void cannotCreateMultipleTenantRecordsWithSameModuleConfigWithoutCode(TestContext testContext)
    throws InterruptedException,
    ExecutionException,
    TimeoutException {

    JsonObject firstConfigRecord = new ConfigurationRecordBuilder()
      .withModuleName("CHECKOUT")
      .withConfigName("other_settings")
      .withValue("some value")
      .create();

    final CompletableFuture<Response> firstRecordCreated = createConfigRecord(firstConfigRecord);

    //Make sure the first record is created before the second
    final Response firstRecordResponse = firstRecordCreated.get(5, TimeUnit.SECONDS);

    JsonObject secondConfigRecord = new ConfigurationRecordBuilder()
      .withModuleName("CHECKOUT")
      .withConfigName("other_settings")
      .withValue("some other value")
      .create();

    final CompletableFuture<Response> secondRecordCreated = createConfigRecord(secondConfigRecord);

    final Response secondRecordResponse = secondRecordCreated.get(5, TimeUnit.SECONDS);

    testContext.assertEquals(201, firstRecordResponse.getStatusCode(),
      String.format(UNEXPECTED_STATUS_CODE, firstRecordResponse.getStatusCode(),
        firstRecordResponse.getBody()));

    testContext.assertEquals(422, secondRecordResponse.getStatusCode(),
      String.format(UNEXPECTED_STATUS_CODE, secondRecordResponse.getStatusCode(),
        secondRecordResponse.getBody()));
  }

  @Test
  public void cannotCreateMultipleUserRecordsWithSameModuleConfigAndCode(
    TestContext testContext)
    throws InterruptedException,
    ExecutionException,
    TimeoutException {

    final UUID userId = UUID.randomUUID();

    JsonObject firstConfigRecord = new ConfigurationRecordBuilder()
      .withModuleName("CHECKOUT")
      .withConfigName("other_settings")
      .withCode("audioAlertsEnabled")
      .withValue("some value")
      .forUser(userId)
      .create();

    final CompletableFuture<Response> firstRecordCreated = createConfigRecord(firstConfigRecord);

    //Make sure the first record is created before the second
    final Response firstRecordResponse = firstRecordCreated.get(5, TimeUnit.SECONDS);

    JsonObject secondConfigRecord = new ConfigurationRecordBuilder()
      .withModuleName("CHECKOUT")
      .withConfigName("other_settings")
      .withCode("audioAlertsEnabled")
      .withValue("some other value")
      .forUser(userId)
      .create();

    final CompletableFuture<Response> secondRecordCreated = createConfigRecord(secondConfigRecord);

    final Response secondRecordResponse = secondRecordCreated.get(5, TimeUnit.SECONDS);

    testContext.assertEquals(201, firstRecordResponse.getStatusCode(),
      String.format(UNEXPECTED_STATUS_CODE, firstRecordResponse.getStatusCode(),
        firstRecordResponse.getBody()));

    testContext.assertEquals(422, secondRecordResponse.getStatusCode(),
      String.format(UNEXPECTED_STATUS_CODE, secondRecordResponse.getStatusCode(),
        secondRecordResponse.getBody()));
  }

  @Test
  public void cannotCreateMultipleUserRecordsWithSameModuleConfigWithoutCode(
    TestContext testContext)
    throws InterruptedException,
    ExecutionException,
    TimeoutException {

    final UUID userId = UUID.randomUUID();

    JsonObject firstConfigRecord = new ConfigurationRecordBuilder()
      .withModuleName("CHECKOUT")
      .withConfigName("other_settings")
      .withNoCode()
      .withValue("some value")
      .forUser(userId)
      .create();

    final CompletableFuture<Response> firstRecordCreated = createConfigRecord(firstConfigRecord);

    //Make sure the first record is created before the second
    final Response firstRecordResponse = firstRecordCreated.get(5, TimeUnit.SECONDS);

    JsonObject secondConfigRecord = new ConfigurationRecordBuilder()
      .withModuleName("CHECKOUT")
      .withConfigName("other_settings")
      .withNoCode()
      .withValue("some other value")
      .forUser(userId)
      .create();

    final CompletableFuture<Response> secondRecordCreated = createConfigRecord(secondConfigRecord);

    final Response secondRecordResponse = secondRecordCreated.get(5, TimeUnit.SECONDS);

    testContext.assertEquals(201, firstRecordResponse.getStatusCode(),
      String.format(UNEXPECTED_STATUS_CODE, firstRecordResponse.getStatusCode(),
        firstRecordResponse.getBody()));

    testContext.assertEquals(422, secondRecordResponse.getStatusCode(),
      String.format(UNEXPECTED_STATUS_CODE, secondRecordResponse.getStatusCode(),
        secondRecordResponse.getBody()));
  }

  //Only a single example, rather than replicating all of the examples used for POST
  @Test
  public void cannotReplaceTenantConfigurationRecordToHaveDuplicateModuleConfigNameAndCode(
    TestContext testContext)
    throws InterruptedException,
    ExecutionException,
    TimeoutException {

    JsonObject firstConfigRecord = new ConfigurationRecordBuilder()
      .withModuleName("CHECKOUT")
      .withConfigName("other_settings")
      .withCode("some_setting")
      .withValue("some value")
      .create();

    final CompletableFuture<Response> firstRecordCreated = createConfigRecord(firstConfigRecord);

    //Make sure the first record is created before the second
    final Response firstRecordResponse = firstRecordCreated.get(5, TimeUnit.SECONDS);

    JsonObject recordToBeUpdated = ConfigurationRecordExamples
      .audioAlertsExample()
      .create();

    final CompletableFuture<Response> postCompleted = createConfigRecord(recordToBeUpdated);

    final Response response = postCompleted.get(5, TimeUnit.SECONDS);

    final JsonObject createdRecord = response.getBodyAsJson();
    String id = createdRecord.getString("id");

    JsonObject putRequest = ConfigurationRecordBuilder.from(createdRecord)
      .withModuleName("CHECKOUT")
      .withConfigName("other_settings")
      .withCode("some_setting")
      .withValue("a new value")
      .create();

    final CompletableFuture<Response> putCompleted = okapiHttpClient.put(
      "http://localhost:" + port + "/configurations/entries/" + id,
      putRequest.encodePrettily());

    final Response putResponse = putCompleted.get(5, TimeUnit.SECONDS);

    testContext.assertEquals(422, putResponse.getStatusCode(),
      String.format(UNEXPECTED_STATUS_CODE, putResponse.getStatusCode(),
        putResponse.getBody()));
  }

  @Test
  public void canCreateMultipleDisabledTenantConfigurationRecordsWithCode(
    TestContext testContext) {

    final Async async = testContext.async();

    List<CompletableFuture<Response>> allRecordsFutures = new ArrayList<>();

    final ConfigurationRecordBuilder baselineSetting = new ConfigurationRecordBuilder()
      .withModuleName("CHECKOUT")
      .withConfigName("main_settings")
      .withCode("example_setting")
      .withValue("some value");

    JsonObject tenantConfigRecord = baselineSetting.create();

    allRecordsFutures.add(createConfigRecord(tenantConfigRecord));

    JsonObject firstDisabledConfigRecord = baselineSetting
      .withValue("another value")
      .disabled()
      .create();

    allRecordsFutures.add(createConfigRecord(firstDisabledConfigRecord));

    JsonObject secondDisabledConfigRecord = baselineSetting
      .withValue("yet another value")
      .disabled()
      .create();

    allRecordsFutures.add(createConfigRecord(secondDisabledConfigRecord));

    CompletableFuture<Void> allRecordsCompleted = allOf(allRecordsFutures);

    allRecordsCompleted.thenAccept(v ->
      checkAllRecordsCreated(allRecordsFutures, testContext, async));
  }

  @Test
  public void canCreateMultipleDisabledTenantConfigurationRecordsWithoutCode(
    TestContext testContext) {

    final Async async = testContext.async();

    List<CompletableFuture<Response>> allRecordsFutures = new ArrayList<>();

    final ConfigurationRecordBuilder baselineSetting = new ConfigurationRecordBuilder()
      .withModuleName("CHECKOUT")
      .withConfigName("main_settings")
      .withNoCode()
      .withValue("some value");

    JsonObject tenantConfigRecord = baselineSetting.create();

    allRecordsFutures.add(createConfigRecord(tenantConfigRecord));

    JsonObject firstDisabledConfigRecord = baselineSetting
      .withValue("another value")
      .disabled()
      .create();

    allRecordsFutures.add(createConfigRecord(firstDisabledConfigRecord));

    JsonObject secondDisabledConfigRecord = baselineSetting
      .withValue("yet another value")
      .disabled()
      .create();

    allRecordsFutures.add(createConfigRecord(secondDisabledConfigRecord));

    CompletableFuture<Void> allRecordsCompleted = allOf(allRecordsFutures);

    allRecordsCompleted.thenAccept(v ->
      checkAllRecordsCreated(allRecordsFutures, testContext, async));
  }

  @Test
  public void canCreateMultipleDisabledUserConfigurationRecordsWithCode(
    TestContext testContext) {

    final Async async = testContext.async();

    List<CompletableFuture<Response>> allRecordsFutures = new ArrayList<>();

    final UUID userId = UUID.randomUUID();

    final ConfigurationRecordBuilder baselineSetting = new ConfigurationRecordBuilder()
      .withModuleName("CHECKOUT")
      .withConfigName("main_settings")
      .withCode("example_setting")
      .withValue("some value")
      .forUser(userId);

    JsonObject tenantConfigRecord = baselineSetting.create();

    allRecordsFutures.add(createConfigRecord(tenantConfigRecord));

    JsonObject firstDisabledConfigRecord = baselineSetting
      .withValue("another value")
      .disabled()
      .create();

    allRecordsFutures.add(createConfigRecord(firstDisabledConfigRecord));

    JsonObject secondDisabledConfigRecord = baselineSetting
      .withValue("yet another value")
      .disabled()
      .create();

    allRecordsFutures.add(createConfigRecord(secondDisabledConfigRecord));

    CompletableFuture<Void> allRecordsCompleted = allOf(allRecordsFutures);

    allRecordsCompleted.thenAccept(v ->
      checkAllRecordsCreated(allRecordsFutures, testContext, async));
  }

  @Test
  public void canCreateMultipleDisabledUserConfigurationRecordsWithoutCode(
    TestContext testContext) {

    final Async async = testContext.async();

    List<CompletableFuture<Response>> allRecordsFutures = new ArrayList<>();

    final UUID userId = UUID.randomUUID();

    final ConfigurationRecordBuilder baselineSetting = new ConfigurationRecordBuilder()
      .withModuleName("CHECKOUT")
      .withConfigName("main_settings")
      .withNoCode()
      .withValue("some value")
      .forUser(userId);

    JsonObject tenantConfigRecord = baselineSetting.create();

    allRecordsFutures.add(createConfigRecord(tenantConfigRecord));

    JsonObject firstDisabledConfigRecord = baselineSetting
      .withValue("another value")
      .disabled()
      .create();

    allRecordsFutures.add(createConfigRecord(firstDisabledConfigRecord));

    JsonObject secondDisabledConfigRecord = baselineSetting
      .withValue("yet another value")
      .disabled()
      .create();

    allRecordsFutures.add(createConfigRecord(secondDisabledConfigRecord));

    CompletableFuture<Void> allRecordsCompleted = allOf(allRecordsFutures);

    allRecordsCompleted.thenAccept(v ->
      checkAllRecordsCreated(allRecordsFutures, testContext, async));
  }

  @Test
  public void canGetConfigurationRecords(TestContext testContext) {
    final Async async = testContext.async();

    final ArrayList<CompletableFuture<Response>> allCreated = new ArrayList<>();

    JsonObject firstConfigRecord = ConfigurationRecordExamples.audioAlertsExample().create();

    allCreated.add(createConfigRecord(firstConfigRecord));

    JsonObject secondConfigRecord = ConfigurationRecordExamples.timeOutDurationExample().create();

    allCreated.add(createConfigRecord(secondConfigRecord));

    allOf(allCreated).thenComposeAsync(v ->
      //Must filter to only check out module entries due to default locale records
      okapiHttpClient.get("http://localhost:" + port + "/configurations/entries?query=module==CHECKOUT"))
    .thenAccept(response -> {
      try {
        testContext.assertEquals(200, response.getStatusCode(),
          String.format(UNEXPECTED_STATUS_CODE, response.getStatusCode(),
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

    final CompletableFuture<Response> firstRecordCreated = createConfigRecord(firstConfigRecord);

    //Make sure the first record is created before the second
    firstRecordCreated.get(5, TimeUnit.SECONDS);

    JsonObject secondConfigRecord = ConfigurationRecordExamples.timeOutDurationExample().create();

    final CompletableFuture<Response> secondRecordCreated = createConfigRecord(secondConfigRecord);

    secondRecordCreated.get(5, TimeUnit.SECONDS);

    String encodedQuery = URLEncoder.encode("module==CHECKOUT sortBy metadata.createdDate/sort.descending",
      StandardCharsets.UTF_8.name());

    //Must filter to only check out module entries due to default locale records
    okapiHttpClient.get("http://localhost:" + port + "/configurations/entries" + "?query=" + encodedQuery)
      .thenAccept(response -> {
        try {
          testContext.assertEquals(200, response.getStatusCode(),
            String.format(UNEXPECTED_STATUS_CODE, response.getStatusCode(),
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
            log.debug(r3.result().getResults().size());
            postgresClient.removePersistentCacheResult("mytablecache", r4 -> {
              log.debug(r4.succeeded());

              /* this will probably cause a deadlock as the saveBatch runs within a transaction */

             /*
             List<Object> a = Arrays.asList(new Object[]{new JsonObject("{\"module1\": \"CIRCULATION\"}"),
                  new JsonObject("{\"module1\": \"CIRCULATION15\"}"), new JsonObject("{\"module1\": \"CIRCULATION\"}")});
              try {
                PostgresClient.getInstance(vertx, "harvard").saveBatch("config_data", a, reply1 -> {
                  if(reply1.succeeded()){
                    log.debug(new io.vertx.core.json.JsonArray( reply1.result().getResults() ).encodePrettily());
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

      log.debug(updatedConf);
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
        log.debug(header.getKey() + " " + header.getValue()));

      int statusCode = response.statusCode();
      if(method == HttpMethod.POST && statusCode == 201){
        try {
          log.debug("Location - " + response.getHeader("Location"));
          Config conf =  new ObjectMapper().readValue(content, Config.class);
          conf.setDescription(conf.getDescription());
          mutateURLs("http://localhost:" + port + response.getHeader("Location"), context, HttpMethod.PUT,
            new ObjectMapper().writeValueAsString(conf), "application/json", 204);
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
      log.debug("Status - " + statusCode + " at " + System.currentTimeMillis() + " for " + url);
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
      log.debug("complete");
    });
    request.setChunked(true);
    request.putHeader("X-Okapi-Request-Id", "999999999999");
    request.putHeader("Authorization", TENANT_ID);
    request.putHeader("x-Okapi-Tenant", TENANT_ID);
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
          String.format(UNEXPECTED_STATUS_CODE, response.getStatusCode(),
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

  private CompletableFuture<Response> createConfigRecord(JsonObject record) {
    return okapiHttpClient.post(
      "http://localhost:" + port + "/configurations/entries",
      record.encodePrettily());
  }
}
