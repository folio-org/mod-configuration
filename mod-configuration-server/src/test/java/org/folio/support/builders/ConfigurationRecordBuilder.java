package org.folio.support.builders;

import io.vertx.core.json.JsonObject;

public class ConfigurationRecordBuilder {
  public JsonObject create(
    String code,
    Object value,
    String description) {

    return new JsonObject()
      .put("module", "CHECKOUT")
      .put("configName", "other_settings")
      .put("description", description)
      .put("code", code)
      .put("value", value);
  }
}
