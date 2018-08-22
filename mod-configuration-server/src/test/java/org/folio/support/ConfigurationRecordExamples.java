package org.folio.support;

import org.folio.support.builders.ConfigurationRecordBuilder;

public class ConfigurationRecordExamples {
  private ConfigurationRecordExamples() { }

  public static ConfigurationRecordBuilder timeOutDurationExample() {
    return new ConfigurationRecordBuilder()
      .withModuleName("CHECKOUT")
      .withConfigName("other_settings")
      .withCode("checkoutTimeoutDuration")
      .withValue(3)
      .withDescription("How long the timeout for a check out session should be");
  }

  public static ConfigurationRecordBuilder audioAlertsExample() {
    return new ConfigurationRecordBuilder()
      .withModuleName("CHECKOUT")
      .withConfigName("other_settings")
      .withCode("audioAlertsEnabled")
      .withValue(true)
      .withDescription("Whether audio alerts should be made during check out");
  }
}
