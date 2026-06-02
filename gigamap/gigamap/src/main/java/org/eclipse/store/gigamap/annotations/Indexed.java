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
 * Marks an entity type whose indices are defined declaratively via {@link Index}, {@link Unique},
 * {@link Identity} and {@link SpatialIndex} (and any integration-module annotations such as full-text
 * or vector annotations).
 * <p>
 * This is a documentation / discovery marker; it is not required for annotation-based index
 * generation to work. It signals intent and can be used by tooling or convenience entry points to
 * recognize annotated entity types.
 *
 * @see Index
 * @see org.eclipse.store.gigamap.types.IndexerGenerator
 */
@Retention(RUNTIME)
@Target(TYPE)
public @interface Indexed
{
	// marker annotation
}
