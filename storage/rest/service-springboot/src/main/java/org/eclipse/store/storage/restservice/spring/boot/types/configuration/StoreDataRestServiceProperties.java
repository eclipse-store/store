package org.eclipse.store.storage.restservice.spring.boot.types.configuration;

/*-
 * #%L
 * EclipseStore Storage REST Service SpringBoot
 * %%
 * Copyright (C) 2023 - 2024 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration of REST service.
 */
@ConfigurationProperties(prefix = "org.eclipse.store.rest")
public class StoreDataRestServiceProperties {
  /**
   * Default base url of the REST service.
   */
  public static final String DEFAULT_BASE_URL = "/store-data";

  /**
   * Flag controlling if REST service is enabled, defaults to <code>false</code>.
   */
  private boolean enabled = false;
  /**
   * Base path of the REST service. Appended to the hostname and port this forms the URL to be entered in the Console Client app.
   */
  private String baseUrl = DEFAULT_BASE_URL; // add validation rules to have a better feedback and IDE support.

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getBaseUrl() {
    return baseUrl;
  }

  public void setBaseUrl(String baseUrl) {
    this.baseUrl = baseUrl;
  }
}
