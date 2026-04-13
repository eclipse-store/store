package org.eclipse.store.demo.vinoteca;

/*-
 * #%L
 * EclipseStore Demo Vinoteca
 * %%
 * Copyright (C) 2023 - 2026 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.theme.Theme;
import com.vaadin.flow.theme.lumo.Lumo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot entry point of the Vinoteca demo.
 * <p>
 * Boots an embedded Tomcat that serves the Vaadin UI, the GraphQL API and the REST/OpenAPI
 * endpoints on port {@code 8082} (see {@code application.properties}). The
 * {@code integrations-spring-boot3} starter auto-configures the EclipseStore
 * {@link org.eclipse.store.storage.embedded.types.EmbeddedStorageManager EmbeddedStorageManager}
 * with {@link org.eclipse.store.demo.vinoteca.model.DataRoot DataRoot} as its persistent root,
 * and {@link org.eclipse.store.demo.vinoteca.service.DataGeneratorService DataGeneratorService}
 * seeds an initial dataset on first start.
 * <p>
 * Implements {@link AppShellConfigurator} so the {@link Push} and {@link Theme} annotations apply
 * to the Vaadin app shell; the dark Lumo theme is enabled by default.
 */
@SpringBootApplication
@Push
@Theme(variant = Lumo.DARK)
public class VinotecaApplication implements AppShellConfigurator
{
	/**
	 * Standard Spring Boot launcher.
	 *
	 * @param args command-line arguments forwarded to Spring Boot
	 */
	public static void main(final String[] args)
	{
		SpringApplication.run(VinotecaApplication.class, args);
	}
}
