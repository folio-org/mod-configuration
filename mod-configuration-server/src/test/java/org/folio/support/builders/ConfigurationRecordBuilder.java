package org.folio.support.builders;

import io.vertx.core.json.JsonObject;

public class ConfigurationRecordBuilder extends JsonBuilder {

  private String code;
  private Object value;
  private String description;

  public ConfigurationRecordBuilder(
    String code,
    Object value,
    String description) {

    this.code = code;
    this.value = value;
    this.description = description;
  }

  public JsonObject create() {
    final JsonObject configurationRecord = new JsonObject();

    put(configurationRecord, "module", "CHECKOUT");
    put(configurationRecord, "configName", "other_settings");
    put(configurationRecord, "description", this.description);
    put(configurationRecord, "code", this.code);
    put(configurationRecord, "value", this.value);

    return configurationRecord;
  }
}
