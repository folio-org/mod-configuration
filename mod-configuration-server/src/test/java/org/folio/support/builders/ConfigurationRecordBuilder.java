package org.folio.support.builders;

import io.vertx.core.json.JsonObject;

public class ConfigurationRecordBuilder extends JsonBuilder {

  private String moduleName;
  private String code;
  private Object value;
  private String description;

  public ConfigurationRecordBuilder() {
    this(null, null, null, null);
  }

  private ConfigurationRecordBuilder(
    String moduleName,
    String code,
    Object value,
    String description) {

    this.moduleName = moduleName;
    this.code = code;
    this.value = value;
    this.description = description;
  }

  public JsonObject create() {
    final JsonObject configurationRecord = new JsonObject();

    put(configurationRecord, "module", this.moduleName);
    put(configurationRecord, "configName", "other_settings");
    put(configurationRecord, "description", this.description);
    put(configurationRecord, "code", this.code);
    put(configurationRecord, "value", this.value);

    return configurationRecord;
  }

  public ConfigurationRecordBuilder withModuleName(String moduleName) {
    return new ConfigurationRecordBuilder(
      moduleName,
      this.code,
      this.value,
      this.description);
  }

  public ConfigurationRecordBuilder withCode(String code) {
    return new ConfigurationRecordBuilder(
      this.moduleName,
      code,
      this.value,
      this.description);
  }

  public ConfigurationRecordBuilder withValue(Object value) {
    return new ConfigurationRecordBuilder(
      this.moduleName,
      this.code,
      value,
      this.description);
  }

  public ConfigurationRecordBuilder withDescription(String description) {
    return new ConfigurationRecordBuilder(
      this.moduleName,
      this.code,
      this.value,
      description);
  }
}
