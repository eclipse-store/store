
package org.eclipse.store.storage.restclient.app.standalone.types;

/*-
 * #%L
 * EclipseStore Storage REST Client App
 * %%
 * Copyright (C) 2023 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import org.eclipse.store.storage.restclient.app.types.RestClientAppAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

import org.springframework.context.annotation.Import;


/**
 * Runnable standalone assembly of the Vaadin-based storage REST client GUI.
 *
 * @deprecated This standalone Vaadin/Spring client GUI is deprecated and scheduled for removal.
 * It pulls in Vaadin and Spring, which makes it heavyweight to run and embed.
 * <p>
 * Use the dependency-free Vanilla-JS storage viewer instead: it is bundled in
 * {@code storage-restservice-javalin} and served from the REST service itself when the
 * {@code eclipse_store_rest_ui_enabled} flag is set to {@code true}. See the "Client GUI"
 * section of the storage REST interface documentation.
 */
@Deprecated(since = "5.0", forRemoval = true)
@SuppressWarnings("deprecation")
@SpringBootApplication
@Import(RestClientAppAutoConfiguration.class)
public class Application extends SpringBootServletInitializer
{
	public static void main(
		final String[] args
	)
	{
		SpringApplication.run(Application.class, args);
	}

}
