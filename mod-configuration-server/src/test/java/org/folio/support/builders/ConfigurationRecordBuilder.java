package org.folio.support.builders;

import io.vertx.core.json.JsonObject;

import java.util.UUID;

public class ConfigurationRecordBuilder extends JsonBuilder {

  private final UUID id;
  private final String moduleName;
  private final String configName;
  private final String code;
  private final Object value;
  private final String description;
  private final Boolean enabled;
  private final UUID userId;

  public ConfigurationRecordBuilder() {
    this(null, null, null, null, null, null, true, null);
  }

  private ConfigurationRecordBuilder(
    UUID id,
    String moduleName,
    String configName,
    String code,
    Object value,
    String description,
    Boolean enabled,
    UUID userId) {

    this.id = id;
    this.moduleName = moduleName;
    this.code = code;
    this.value = value;
    this.description = description;
    this.configName = configName;
    this.enabled = enabled;
    this.userId = userId;
  }

  public static ConfigurationRecordBuilder from(String example) {
    return from(new JsonObject(example));
  }

  public static ConfigurationRecordBuilder from(JsonObject example) {
    //TODO: Extract constants for properties

    final UUID id = example.containsKey("id")
      ? UUID.fromString(example.getString("id"))
      : null;

    final UUID userId = example.containsKey("userId")
      ? UUID.fromString(example.getString("userId"))
      : null;

    return new ConfigurationRecordBuilder(
      id,
      example.getString("module"),
      example.getString("configName"),
      example.getString("code"),
      example.getValue("value"),
      example.getString("description"),
      example.getBoolean("enabled"),
      userId);
  }

  public JsonObject create() {
    final JsonObject configurationRecord = new JsonObject();

    put(configurationRecord, "module", this.moduleName);
    put(configurationRecord, "configName", configName);
    put(configurationRecord, "description", this.description);
    put(configurationRecord, "code", this.code);
    put(configurationRecord, "value", this.value);
    put(configurationRecord, "enabled", this.enabled);
    put(configurationRecord, "userId", this.userId);

    return configurationRecord;
  }

  public ConfigurationRecordBuilder withId(UUID id) {
    return new ConfigurationRecordBuilder(
      id,
      this.moduleName,
      this.configName,
      this.code,
      this.value,
      this.description,
      this.enabled,
      this.userId);
  }

  public ConfigurationRecordBuilder withModuleName(String moduleName) {
    return new ConfigurationRecordBuilder(
      this.id,
      moduleName,
      this.configName,
      this.code,
      this.value,
      this.description,
      this.enabled,
      this.userId);
  }

  public ConfigurationRecordBuilder withConfigName(String configName) {
    return new ConfigurationRecordBuilder(
      this.id,
      this.moduleName,
      configName,
      this.code,
      this.value,
      this.description,
      this.enabled,
      this.userId);
  }

  public ConfigurationRecordBuilder withCode(String code) {
    return new ConfigurationRecordBuilder(
      this.id,
      this.moduleName,
      this.configName,
      code,
      this.value,
      this.description,
      this.enabled,
      this.userId);
  }

  public ConfigurationRecordBuilder withNoCode() {
    return withCode(null);
  }

  public ConfigurationRecordBuilder withValue(Object value) {
    return new ConfigurationRecordBuilder(
      this.id,
      this.moduleName,
      this.configName,
      this.code,
      value,
      this.description,
      this.enabled,
      this.userId);
  }

  public ConfigurationRecordBuilder withDescription(String description) {
    return new ConfigurationRecordBuilder(
      this.id,
      this.moduleName,
      this.configName,
      this.code,
      this.value,
      description,
      this.enabled,
      this.userId);
  }

  public ConfigurationRecordBuilder disabled() {
    return new ConfigurationRecordBuilder(
      this.id,
      this.moduleName,
      this.configName,
      this.code,
      this.value,
      this.description,
      false,
      this.userId);
  }

  public ConfigurationRecordBuilder forUser(UUID userId) {
    return new ConfigurationRecordBuilder(
      this.id,
      this.moduleName,
      this.configName,
      this.code,
      this.value,
      this.description,
      this.enabled,
      userId);
  }

  public ConfigurationRecordBuilder forNoUser() {
    return forUser(null);
  }
}
