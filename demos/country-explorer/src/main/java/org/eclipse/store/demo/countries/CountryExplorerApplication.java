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

@SpringBootApplication
public class CountryExplorerApplication
{
	public static void main(final String[] args)
	{
		SpringApplication.run(CountryExplorerApplication.class, args);
	}
}
