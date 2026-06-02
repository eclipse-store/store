package org.eclipse.store.gigamap.annotations;

/*-
 * #%L
 * EclipseStore GigaMap
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

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Declares a spatial (geographic) index on an entity type, built from two coordinate properties.
 * <p>
 * Unlike {@link Index}, which is applied per field, a spatial index spans two members (latitude and
 * longitude) and is therefore declared at the type level. The referenced members may be fields or
 * no-argument getter methods and must yield a numeric ({@code double} / {@link Double}) value; both
 * must be {@code null} together to represent "no coordinates".
 * <p>
 * The generated index is a bitmap-based spatial index equivalent to a hand-written
 * {@code SpatialIndexer.Abstract}, supporting {@code near}, {@code withinBox} and per-axis range
 * queries.
 *
 * <pre>{@code
 * @SpatialIndex(latitude = "lat", longitude = "lon")
 * public class City {
 *     double lat;
 *     double lon;
 *     // ...
 * }
 * }</pre>
 *
 * @see Index
 * @see org.eclipse.store.gigamap.types.SpatialIndexer
 */
@Retention(RUNTIME)
@Target(TYPE)
public @interface SpatialIndex
{
	/**
	 * The name of the latitude property (field or no-argument getter) on the entity.
	 *
	 * @return the latitude member name
	 */
	public String latitude();

	/**
	 * The name of the longitude property (field or no-argument getter) on the entity.
	 *
	 * @return the longitude member name
	 */
	public String longitude();

	/**
	 * Specify a custom name for the index. Defaults to {@code "spatial"} when empty.
	 *
	 * @return custom index name
	 */
	public String name() default "";
}
