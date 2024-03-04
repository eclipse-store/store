
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
