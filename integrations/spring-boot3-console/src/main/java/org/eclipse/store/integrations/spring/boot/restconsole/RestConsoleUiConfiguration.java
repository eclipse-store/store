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

import org.eclipse.store.storage.restclient.app.types.RestClientAppAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@ConditionalOnProperty(value = "org.eclipse.store.console.ui.enabled", havingValue = "true", matchIfMissing = true)
@Configuration
@Import(RestClientAppAutoConfiguration.class)
public class RestConsoleUiConfiguration
{
}
