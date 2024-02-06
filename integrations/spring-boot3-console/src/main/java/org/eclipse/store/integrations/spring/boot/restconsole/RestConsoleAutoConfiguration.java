package org.eclipse.store.integrations.spring.boot.restconsole;

/*-
 * #%L
 * EclipseStore Integrations SpringBoot Console
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

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RestConsoleAutoConfiguration {

  private final Logger logger = LoggerFactory.getLogger(RestConsoleAutoConfiguration.class);

  @PostConstruct
  public void initialize() {
    logger.warn("[ECLIPSE STORE CONSOLE]: Starting rest console.");
  }
}
