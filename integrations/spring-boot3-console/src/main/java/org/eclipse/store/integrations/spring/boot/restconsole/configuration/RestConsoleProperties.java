package org.eclipse.store.integrations.spring.boot.restconsole.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

@ConfigurationProperties(prefix = "org.eclipse.store.console")
public class RestConsoleProperties {

  @NestedConfigurationProperty
  private UIProperties ui = new UIProperties();

  public UIProperties getUi() {
    return ui;
  }

  public void setUi(UIProperties ui) {
    this.ui = ui;
  }
}
