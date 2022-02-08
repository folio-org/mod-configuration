package org.folio.support.builders;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.util.UUID;

public abstract class JsonBuilder {
  void put(JsonObject representation, String property, String value) {
    if(value != null) {
      representation.put(property, value);
    }
  }

  void put(JsonObject representation, String property, Object value) {
    if(value != null) {
      representation.put(property, value);
    }
  }

  protected void put(JsonObject representation, String property, Integer value) {
    if(value != null) {
      representation.put(property, value);
    }
  }

  protected void put(JsonObject representation, String property, UUID value) {
    if(value != null) {
      representation.put(property, value.toString());
    }
  }

  protected void put(JsonObject representation, String property, Boolean value) {
    if(value != null) {
      representation.put(property, value);
    }
  }

  protected void put(JsonObject representation, String property, JsonObject value) {
    if(value != null) {
      representation.put(property, value);
    }
  }

  protected void put(
    JsonObject representation,
    String property,
    Object check,
    JsonObject value) {

    if(check != null) {
      representation.put(property, value);
    }
  }

  protected void put(JsonObject representation, String property, JsonArray value) {
    if(value != null) {
      representation.put(property, value);
    }
  }
}
