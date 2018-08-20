package org.folio.support.builders;

import io.vertx.core.json.JsonObject;

public class ConfigurationRecordBuilder extends JsonBuilder {
  public JsonObject create(
    String code,
    Object value,
    String description) {

    final JsonObject configurationRecord = new JsonObject();

    put(configurationRecord, "module", "CHECKOUT");
    put(configurationRecord, "configName", "other_settings");
    put(configurationRecord, "description", description);
    put(configurationRecord, "code", code);
    put(configurationRecord, "value", value);

    return configurationRecord;
  }
}
