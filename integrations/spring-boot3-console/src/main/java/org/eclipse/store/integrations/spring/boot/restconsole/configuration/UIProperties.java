package org.eclipse.store.integrations.spring.boot.restconsole.configuration;

public class UIProperties {
  /**
   * Flag controlling if UI console be enabled.
   *
   */
  private boolean enabled = true;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }
}
