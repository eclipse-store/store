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

/**
 * Immutable data record representing a country and its core geographic and
 * demographic attributes.
 * <p>
 * Instances are typically deserialized from the bundled {@code countries.json}
 * resource and stored in a
 * {@link org.eclipse.store.gigamap.types.GigaMap GigaMap} for indexed querying.
 *
 * @param name       the common English name of the country (e.g. {@code "Germany"})
 * @param alpha2     the ISO&nbsp;3166-1 alpha-2 country code (e.g. {@code "DE"})
 * @param alpha3     the ISO&nbsp;3166-1 alpha-3 country code (e.g. {@code "DEU"})
 * @param capital    the name of the country's capital city (e.g. {@code "Berlin"})
 * @param latitude   the latitude of the capital in decimal degrees
 * @param longitude  the longitude of the capital in decimal degrees
 * @param continent  the continent the country belongs to (e.g. {@code "Europe"})
 * @param population the estimated population of the country
 * @param area       the total area of the country in square kilometres
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
