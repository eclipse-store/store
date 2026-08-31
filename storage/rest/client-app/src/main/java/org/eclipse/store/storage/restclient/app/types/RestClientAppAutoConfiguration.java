package org.eclipse.store.storage.restclient.app.types;

/*-
 * #%L
 * EclipseStore Storage REST Client App
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

import com.vaadin.flow.spring.annotation.EnableVaadin;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Spring auto-configuration for the Vaadin-based storage REST client GUI.
 *
 * @deprecated The Vaadin/Spring based client GUI ({@code storage-restclient-app} and
 * {@code storage-restclient-app-standalone-assembly}) is deprecated and scheduled for removal.
 * It pulls in Vaadin and Spring, which makes it hard to embed into existing projects.
 * <p>
 * Use the dependency-free Vanilla-JS storage viewer instead: it is bundled in
 * {@code storage-restservice-javalin} and served from the REST service itself when the
 * {@code eclipse_store_rest_ui_enabled} flag is set to {@code true}. See the "Client GUI"
 * section of the storage REST interface documentation.
 */
@Deprecated(since = "5.0", forRemoval = true)
@Configuration
@EnableVaadin("org.eclipse.store.storage.restclient.app.ui")
@ComponentScan
public class RestClientAppAutoConfiguration {
}
