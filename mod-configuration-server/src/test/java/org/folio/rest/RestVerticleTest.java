package org.folio.rest;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.Parameter;
import org.folio.rest.jaxrs.model.TenantAttributes;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.utils.TenantInit;
import org.folio.support.ConfigurationRecordExamples;
import org.folio.support.OkapiHttpClient;
import org.folio.support.Response;
import org.folio.support.builders.ConfigurationRecordBuilder;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.junit.runner.RunWith;


import static org.folio.support.CompletableFutureExtensions.allOf;

/**
 * This is our JUnit test for our verticle. The test uses vertx-unit, so we declare a custom runner.
 */
@RunWith(VertxUnitRunner.class)
public class RestVerticleTest extends TestBase {
  private static final String UNEXPECTED_STATUS_CODE = "Unexpected status code: '%s': '%s'";

  private static final Logger log = LogManager.getLogger(RestVerticleTest.class);

  private static final String TENANT_ID = "harvard";
  private static final String USER_ID = "79ff2a8b-d9c3-5b39-ad4a-0a84025ab085";

  private static final Vertx vertx = Vertx.vertx();
  private static final OkapiHttpClient okapiHttpClient = new OkapiHttpClient(
    vertx, TENANT_ID, USER_ID);

  //@Rule
  //public Timeout timeout = Timeout.seconds(10);

  @Rule
  public TestWatcher watchman = new TestWatcher() {
    @Override
    public void starting(final Description description) {
      log.info("Starting {}", description.getMethodName() );
    }
  };

  @Before
  public void beforeEach(TestContext context)
    throws InterruptedException,
    ExecutionException,
    TimeoutException {

    vertx.exceptionHandler(context.exceptionHandler());  // fail test if exception is not caught

    deleteAllConfigurationRecords()
    .compose(x ->  deleteAllConfigurationAuditRecords())
    .onComplete(context.asyncAssertSuccess());
  }

  @Test
  public void testGetConfigurationsEntriesBadFacets1(TestContext testContext) {
    final Async async = testContext.async();
    okapiHttpClient.get("http://localhost:" + port + "/configurations/entries?query=module==SETTINGS&facets=a,")
      .thenAccept(response -> {
        try {
          testContext.assertEquals(400, response.getStatusCode(),
            String.format(UNEXPECTED_STATUS_CODE, response.getStatusCode(),
              response.getBody()));
        } catch (Exception e) {
          testContext.fail(e);
        } finally {
          async.complete();
        }
      });
  }

  @Test
  public void testGetConfigurationsEntriesBadFacets2(TestContext testContext) {
    final Async async = testContext.async();
    okapiHttpClient.get("http://localhost:" + port + "/configurations/entries?query=module==SETTINGS&facets=,a")
      .thenAccept(response -> {
        try {
          testContext.assertEquals(400, response.getStatusCode(),
            String.format(UNEXPECTED_STATUS_CODE, response.getStatusCode(),
              response.getBody()));
        } catch (Exception e) {
          testContext.fail(e);
        } finally {
          async.complete();
        }
      });
  }

  @Test
  public void testGetConfigurationsEntriesSyntaxError(TestContext testContext) {
    final Async async = testContext.async();
    okapiHttpClient.get("http://localhost:" + port + "/configurations/entries?query=a+and")
      .thenAccept(response -> {
        try {
          testContext.assertEquals(400, response.getStatusCode(),
            String.format(UNEXPECTED_STATUS_CODE, response.getStatusCode(),
              response.getBody()));
        } catch (Exception e) {
          testContext.fail(e);
        } finally {
          async.complete();
        }
      });
  }

  @Test
  public void testGetConfigurationsEntriesNoTenant(TestContext testContext) {
    final Async async = testContext.async();
    okapiHttpClient.get("http://localhost:" + port + "/configurations/entries?query=module==SETTINGS", null)
      .thenAccept(response -> {
        try {
          testContext.assertEquals(400, response.getStatusCode(),
            String.format(UNEXPECTED_STATUS_CODE, response.getStatusCode(),
              response.getBody()));
        } catch (Exception e) {
          testContext.fail(e);
        } finally {
          async.complete();
        }
      });
  }

  /**
   * Test upgrade (2nd Tenant POST)
   * @param context
   */
  @Test
  public void upgradeTenant(TestContext context) {
    assertCreateConfigRecord(new JsonObject().put("module", "ORDERS").put("configName", "prefixes")
      .put("value", new JsonObject().put("selectedItems", new JsonArray().add("foo").add("bar")).encode()));
    assertCreateConfigRecord(new JsonObject().put("module", "ORDERS").put("configName", "suffixes")
      .put("value", new JsonObject().put("selectedItems", new JsonArray().add("baz").add("bee").add("beer")).encode()));

    TenantAttributes ta = new TenantAttributes();
    ta.setModuleTo("mod-configuration-1.0.1");
    ta.setModuleFrom("mod-configuration-1.0.0");
    List<Parameter> parameters = new LinkedList<>();
    parameters.add(new Parameter().withKey("loadSample").withValue("true"));
    ta.setParameters(parameters);

    TenantInit.exec(tenantClient, ta, 6000).onComplete(context.asyncAssertSuccess(res2 -> {
      context.assertEquals(0, getByCql("configName==prefixes").getJsonArray("configs").size());
      context.assertEquals(0, getByCql("configName==suffixes").getJsonArray("configs").size());
      context.assertEquals(2, getByCql("configName==orders.prefix").getJsonArray("configs").size());
      context.assertEquals(3, getByCql("configName==orders.suffix").getJsonArray("configs").size());
    }));
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
    firstRecordCreated.get(5, TimeUnit.SECONDS);

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

    allOf(allCreated).thenRunAsync(() -> {
      try {
        // Must filter to only check out module entries due to default locale records
        testContext.assertEquals(2, getByCql("module==CHECKOUT").getInteger("totalRecords"));
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
  public void canUsePersistentCaching(TestContext context) {
    final PostgresClient postgresClient = PostgresClient.getInstance(vertx, TENANT_ID);

    postgresClient.persistentlyCacheResult("mytablecache",
      "select * from harvard_mod_configuration.config_data where jsonb->>'config_name' = 'validation_rules'",
        context.asyncAssertSuccess(r1 ->
          postgresClient.select("select * from harvard_mod_configuration.mytablecache",
              context.asyncAssertSuccess(r2 ->
            postgresClient.removePersistentCacheResult("mytablecache", context.asyncAssertSuccess())
          ))
      ));
  }


  private static Future<RowSet<Row>> deleteAllConfigurationRecords() {
    return deleteAllConfigurationRecordsFromTable("config_data");
  }

  private static Future<RowSet<Row>> deleteAllConfigurationAuditRecords() {
    return deleteAllConfigurationRecordsFromTable("audit_config_data");
  }

  private static Future<RowSet<Row>> deleteAllConfigurationRecordsFromTable(String table) {
    return PostgresClient.getInstance(vertx, TENANT_ID)
        .execute("DELETE FROM " + SCHEMA + "." + table);
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

  private JsonObject assertCreateConfigRecord(JsonObject record) {
    try {
      Response response = okapiHttpClient.post(
        "http://localhost:" + port + "/configurations/entries",
        record.encodePrettily()).get(5, TimeUnit.SECONDS);
      if (response.getStatusCode() != 201) {
        throw new AssertionError("Expected 201 HTTP status code, but got " + response.getStatusCode()
          + ". " + response.getBody());
      }
      return response.getBodyAsJson();
    } catch (InterruptedException | ExecutionException | TimeoutException e) {
      throw new RuntimeException(e);
    }
  }

  private JsonObject getByCql(String cql) {
    try {
      String encodedCql = URLEncoder.encode(cql, StandardCharsets.UTF_8.name());
      Response response  = okapiHttpClient
          .get("http://localhost:" + port + "/configurations/entries" + "?query=" + encodedCql)
          .get(5, TimeUnit.SECONDS);
      if (response.getStatusCode() != 200) {
        throw new AssertionError("Expected 200 HTTP code, but was " + response.getStatusCode()
            + ". " + response.getBody());
      }
      return response.getBodyAsJson();
    } catch (UnsupportedEncodingException | InterruptedException | ExecutionException | TimeoutException e) {
      throw new RuntimeException(e);
    }
  }
}
