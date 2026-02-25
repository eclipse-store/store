package org.eclipse.store.demo.countries.model;

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

public record Country(
	String name,
	String alpha2,
	String alpha3,
	String capital,
	double latitude,
	double longitude,
	String continent,
	long   population,
	double area
) {}
