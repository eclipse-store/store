package org.eclipse.store.demo.countries;

/*-
 * #%L
 * EclipseStore Demo Country Explorer
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

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main entry point for the Country Explorer demo application.
 * <p>
 * This Spring Boot application demonstrates the use of EclipseStore's
 * {@link org.eclipse.store.gigamap.types.GigaMap GigaMap} for indexing and querying
 * geospatial country data. It exposes a REST API that allows clients to search countries
 * by name, continent, geographic proximity, bounding box, hemisphere, and more.
 * <p>
 * On startup the application loads country data from a bundled {@code countries.json}
 * resource into a {@code GigaMap} instance that is configured with bitmap and spatial
 * indices for efficient querying.
 *
 * @see org.eclipse.store.demo.countries.config.GigaMapConfig
 * @see org.eclipse.store.demo.countries.service.CountryService
 */
@SpringBootApplication
public class CountryExplorerApplication
{
	/**
	 * Launches the Spring Boot application.
	 *
	 * @param args command-line arguments forwarded to {@link SpringApplication#run(Class, String...)}
	 */
	public static void main(final String[] args)
	{
		SpringApplication.run(CountryExplorerApplication.class, args);
	}
}
