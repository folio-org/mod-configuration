package org.folio.support.builders;

import io.vertx.core.json.JsonObject;

public class ConfigurationRecordBuilder extends JsonBuilder {

  private final String moduleName;
  private final String configName;
  private final String code;
  private final Object value;
  private final String description;

  public ConfigurationRecordBuilder() {
    this(null, "other_settings", null, null, null);
  }

  private ConfigurationRecordBuilder(
    String moduleName,
    String configName,
    String code,
    Object value,
    String description) {

    this.moduleName = moduleName;
    this.code = code;
    this.value = value;
    this.description = description;
    this.configName = configName;
  }

  public JsonObject create() {
    final JsonObject configurationRecord = new JsonObject();

    put(configurationRecord, "module", this.moduleName);
    put(configurationRecord, "configName", configName);
    put(configurationRecord, "description", this.description);
    put(configurationRecord, "code", this.code);
    put(configurationRecord, "value", this.value);

    return configurationRecord;
  }

  public ConfigurationRecordBuilder withModuleName(String moduleName) {
    return new ConfigurationRecordBuilder(
      moduleName,
      this.configName,
      this.code,
      this.value,
      this.description
    );
  }

  public ConfigurationRecordBuilder withConfigName(String configName) {
    return new ConfigurationRecordBuilder(
      this.moduleName,
      configName,
      this.code,
      this.value,
      this.description
    );
  }

  public ConfigurationRecordBuilder withCode(String code) {
    return new ConfigurationRecordBuilder(
      this.moduleName,
      this.configName,
      code,
      this.value,
      this.description
    );
  }

  public ConfigurationRecordBuilder withValue(Object value) {
    return new ConfigurationRecordBuilder(
      this.moduleName,
      this.configName,
      this.code,
      value,
      this.description
    );
  }

  public ConfigurationRecordBuilder withDescription(String description) {
    return new ConfigurationRecordBuilder(
      this.moduleName,
      this.configName,
      this.code,
      this.value,
      description
    );
  }
}
