package org.folio.rest;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.RunTestOnContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.client.HttpRequest;
import java.util.Base64;
import java.util.Date;

import org.folio.okapi.common.ChattyHttpResponseExpectation;
import org.folio.rest.jaxrs.model.Config;
import org.folio.rest.jaxrs.model.Metadata;
import org.folio.support.builders.ConfigurationRecordBuilder;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class CheckUrlTest extends TestBase {
  private Vertx vertx;

  @Rule
  public RunTestOnContext runTestOnContext = new RunTestOnContext();

  @BeforeClass
  public static void createSampleRecords(TestContext context) throws Exception {
    ConfigurationRecordBuilder baselineFromSample = new ConfigurationRecordBuilder()
        .withModuleName("DUMMY")
        .withDescription("dummy module for testing")
        .withCode("config_data")
        .withConfigName("dummy_rules")
        .withValue("")
        .withDefault();

    mutateURLs("http://localhost:" + port + "/configurations/entries", context, HttpMethod.POST,
        baselineFromSample.create().encodePrettily(), "application/json", 201);

    String testEncodedData = "this string represents config data for the module, to be posted under a given code";
    // save config entry with value being a base64 encoded file
    String bytes = Base64.getEncoder().encodeToString(testEncodedData.getBytes());

    ConfigurationRecordBuilder encodedValueExample = baselineFromSample.withCode("encoded_example").withValue(bytes);

    mutateURLs("http://localhost:" + port + "/configurations/entries", context, HttpMethod.POST,
        encodedValueExample.create().encodePrettily(), "application/json", 201);

    ConfigurationRecordBuilder disabledExample = baselineFromSample.withCode("enabled_example").withValue(bytes)
        .disabled();

    mutateURLs("http://localhost:" + port + "/configurations/entries", context, HttpMethod.POST,
        disabledExample.create().encodePrettily(), "application/json", 201);

    mutateURLs("http://localhost:" + port + "/configurations/entries/123456", context, HttpMethod.DELETE, "",
        "application/json", 400);

    mutateURLs("http://localhost:" + port + "/admin/kill_query?pid=11", context, HttpMethod.DELETE, "",
        "application/json", 404);

    String baselineAsString = baselineFromSample.create().toString();

    // check read only
    Config conf2 = new ObjectMapper().readValue(baselineAsString, Config.class);

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

    mutateURLs("http://localhost:" + port + "/configurations/entries", context, HttpMethod.POST, updatedConf,
        "application/json", 201);
  }

  @Before
  public void beforeEach(TestContext context) {
    vertx = runTestOnContext.vertx();
    vertx.exceptionHandler(context.exceptionHandler());  // fail test if exception is not caught
  }

  private void checkUrl(TestContext context, String path, int expectedStatus, int expectedTotalRecords) {
    webClient
    .get(path)
    .putHeader("x-okapi-tenant", TENANT_ID)
    .send()
    .onComplete(context.asyncAssertSuccess(httpResponse -> {
      assertThat("HTTP status code", httpResponse.statusCode(), is(expectedStatus));
      if (expectedTotalRecords >= 0) {
        assertThat("totalRecords", httpResponse.bodyAsJsonObject().getInteger("totalRecords"), is(expectedTotalRecords));
      }
    }));
  }

  @Test
  public void code(TestContext context) {
    checkUrl(context, "/configurations/entries?facets=code&facets=module", 200, 4);
  }

  @Test
  public void dummyFacets(TestContext context) {
    checkUrl(context, "/configurations/entries?query=module==DUMMY&facets=code&facets=module", 200, 4);
  }

  @Test
  public void dummy3Facets(TestContext context) {
    checkUrl(context, "/configurations/entries?query=module==DUMMY3&facets=code&facets=module", 200, 0);
  }

  @Test
  public void facets(TestContext context) {
    checkUrl(context, "/configurations/entries?query=code=config_data*%20sortBy%20code&facets=code&facets=module", 200, 1);
  }

  @Test
  public void facets4(TestContext context) {
    checkUrl(context, "/configurations/entries?query=code=config_data*%20sortBy%20code&facets=code:4&facets=module", 200, 1);
  }

  @Test
  public void sort(TestContext context) {
    checkUrl(context, "/configurations/entries?query=code=config_data*%20sortBy%20code/sort.descending&facets=code:3&facets=module", 200, 1);
  }

  @Test
  public void code1(TestContext context) {
    checkUrl(context, "/configurations/entries?facets=code1&facets=module", 200 , 4);
  }

  @Test
  public void notFound(TestContext context) {
    checkUrl(context, "/configurations/entries/91287080-a81c-4a84-8d34-39cd9fedd8b5", 404, -1);
  }

  @Test
  public void invalidUuid(TestContext context) {
    checkUrl(context, "/configurations/entries/91287080-a81c-4a84-8d3439cd9fedd8b5", 404, -1);
  }

  @Test
  public void dummy(TestContext context) {
    checkUrl(context, "/configurations/entries?query=module==DUMMY", 200 , 4);
  }

  @Test
  public void enabled(TestContext context) {
    checkUrl(context, "/configurations/entries?query=scope.institution_id=aaa%20sortBy%20enabled", 200 , 0);
  }

  @Test
  public void audit(TestContext context) {
    checkUrl(context, "/configurations/audit", 200 , 8);
  }

  @Test
  public void locks(TestContext context) {
    checkUrl(context, "/admin/list_locking_queries", 200, -1);
  }

  @Test
  public void defaultFacets(TestContext context) {
    checkUrl(context, "/configurations/entries?query=default==true&facets=code&facets=module", 200, 4);
  }

  @Test
  public void defaultTrue(TestContext context) {
    checkUrl(context, "/configurations/entries?query=default==true", 200 , 4);
  }

  @Test
  public void defaultSort(TestContext context) {
    checkUrl(context, "/configurations/entries?query=default==true%20sortBy%20code/sort.descending", 200 , 4);
  }

  @Test
  public void less(TestContext context) {
    checkUrl(context, "/configurations/entries?query=module<1%20sortBy%20code/sort.descending&facets=code&facets=description", 200 , 0);
  }

  @Test
  public void greater(TestContext context) {
    checkUrl(context, "/configurations/entries?query=module>1%20sortBy%20code/sort.descending&facets=module&facets=description", 200 , 4);
  }

  @Test
  public void all(TestContext context) {
    checkUrl(context, "/configurations/entries?query=cql.allRecords=1%20NOT%20userId=\"\"%20or%20userId=\"joeshmoe\"", 200 , 4);
  }

  @Test
  public void canChangeLogLevel(TestContext context) {
    webClient
    .put("/admin/loglevel?level=FINE&java_package=org.folio.rest.persist")
    .putHeader("Content-Type", "application/json")
    .send()
    .expecting(ChattyHttpResponseExpectation.SC_OK)
    .onComplete(context.asyncAssertSuccess());
  }

  private static void mutateURLs(
    String url,
    TestContext context,
    HttpMethod method,
    String content,
    String contentType,
    int expectedStatusCode) {

    Async async = context.async();
    HttpRequest<Buffer> request;
    Buffer buffer = Buffer.buffer(content);

    if (method == HttpMethod.POST) {
      request = webClient.postAbs(url);
    } else if (method == HttpMethod.DELETE) {
      request = webClient.deleteAbs(url);
    } else if (method == HttpMethod.GET) {
      request = webClient.getAbs(url);
    } else {
      request = webClient.putAbs(url);
    }
    request.putHeader("X-Okapi-Request-Id", "999999999999");
    request.putHeader("Authorization", TENANT_ID);
    request.putHeader("x-Okapi-Tenant", TENANT_ID);
    request.putHeader("x-Okapi-User-Id", "79ff2a8b-d9c3-5b39-ad4a-0a84025ab085");
    request.putHeader("Accept", "application/json,text/plain");
    request.putHeader("Content-type", contentType);
    request.sendBuffer(buffer)
        .onFailure(cause -> {
          async.complete();
          context.fail(cause.getMessage());
        })
        .onSuccess(response -> {
          int statusCode = response.statusCode();
          if (method == HttpMethod.POST && statusCode == 201) {
            try {
              Config conf = new ObjectMapper().readValue(content, Config.class);
              conf.setDescription(conf.getDescription());
              mutateURLs("http://localhost:" + port + response.getHeader("Location"), context, HttpMethod.PUT,
                  new ObjectMapper().writeValueAsString(conf), "application/json", 204);
            } catch (Exception e) {
              e.printStackTrace();
            }
          }
          if (expectedStatusCode == statusCode) {
            context.assertTrue(true);
          } else if (expectedStatusCode == 0) {
            //currently don't care about return value
            context.assertTrue(true);
          } else {
            context.fail("expected " + expectedStatusCode + " code, but got " + statusCode);
          }
          if (!async.isCompleted()) {
            async.complete();
          }

        });
  }

}
