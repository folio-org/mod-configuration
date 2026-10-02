package org.folio.it;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.when;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.vertx.core.json.JsonObject;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Test that shaded fat uber jar and Dockerfile work.
 *
 * <p>Smoke tests: /admin/health, install and upgrade.
 */
@Testcontainers
class ModConfigurationIT {

  private static final Logger LOG = LoggerFactory.getLogger(ModConfigurationIT.class);
  private static final Network NETWORK = Network.newNetwork();
  private static final DockerImageName POSTGRES_IMAGE_NAME = DockerImageName.parse(
      Objects.toString(System.getenv("TESTCONTAINERS_POSTGRES_IMAGE"), "postgres:16-alpine"));

  @Container
  @SuppressWarnings("resource")
  static final PostgreSQLContainer POSTGRES =
    new PostgreSQLContainer(POSTGRES_IMAGE_NAME)
    .withNetwork(NETWORK)
    .withNetworkAliases("postgres")
    .withExposedPorts(5432)
    .withUsername("username")
    .withPassword("password")
    .withDatabaseName("postgres");

  @Container
  @SuppressWarnings("resource")
  static final GenericContainer<?> MOD_CONFIGURATION =
    new GenericContainer<>(new ImageFromDockerfile("mod-configuration").withFileFromPath(".", Path.of("..")))
    .dependsOn(POSTGRES)
    .withNetwork(NETWORK)
    .withExposedPorts(8081)
    .withEnv("DB_HOST", "postgres")
    .withEnv("DB_PORT", "5432")
    .withEnv("DB_USERNAME", "username")
    .withEnv("DB_PASSWORD", "password")
    .withEnv("DB_DATABASE", "postgres");

  @BeforeAll
  static void beforeClass() {
    MOD_CONFIGURATION.followOutput(
        new Slf4jLogConsumer(LOG).withSeparateOutputStreams().withPrefix("mod-configuration"));
  }

  private void tenant(String tenant) {
    RestAssured.reset();
    RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    RestAssured.baseURI = "http://" + MOD_CONFIGURATION.getHost() + ":" + MOD_CONFIGURATION.getFirstMappedPort();
    var headers = tenant == null
        ? Map.of("X-Okapi-Url", "http://okapi:8080")
        : Map.of("X-Okapi-Url", "http://okapi:8080", "X-Okapi-Tenant", tenant);
    RestAssured.requestSpecification = new RequestSpecBuilder()
        .addHeaders(headers)
        .setContentType(ContentType.JSON)
        .build();
  }

  @BeforeEach
  void beforeEach() {
    tenant(null);  // unset X-Okapi-Tenant header
  }

  @Test
  void health() {
    when().
      get("/admin/health").
    then().
      statusCode(200).
      body(is("\"OK\""));
  }

  private void postTenant(JsonObject body) {
    String location =
        given().
          body(body.encodePrettily()).
        when().
          post("/_/tenant").
        then().
          statusCode(201).
        extract().
          header("Location");

    when().
      get(location + "?wait=30000").
    then().
      statusCode(200).  // getting job record succeeds
      body("complete", is(true)).  // job is complete
      body("error", is(nullValue()));  // job has succeeded without error
  }

  @Test
  void installAndUpgrade() {
    tenant("latest");
    postTenant(new JsonObject().put("module_to", "mod-configuration-999999.0.0"));
    // migrate from 0.0.0, migration should be idempotent
    postTenant(new JsonObject().put("module_to", "mod-configuration-999999.0.0")
        .put("module_from", "mod-configuration-0.0.0"));

    var id = given().
      body("""
          {
            "module": "mod-x",
            "configName": "foo",
            "value": "bar"
          }
          """).
      when().
        post("/configurations/entries").
      then().
        statusCode(201).
      extract().
        path("id").toString();

    when().
      get("/configurations/entries/" + id).
    then().
      statusCode(200).
      body("module", is("mod-x")).
      body("configName", is("foo")).
      body("value", is("bar"));
  }

}
