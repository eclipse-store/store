package org.eclipse.store.gigamap.lucene.annotations;

/*-
 * #%L
 * EclipseStore GigaMap Lucene
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

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Marks a field or no-argument getter to be indexed in a Lucene full-text index.
 * <p>
 * An entity type carrying at least one {@code @FullText} member can be wired into annotation-based
 * index generation via
 * {@code IndexerGenerator.AnnotationBased(EntityType.class).register(LuceneAnnotationHandler.New()).generateIndices(map)}.
 * The handler builds a {@code DocumentPopulator} that maps every {@code @FullText} member into a
 * Lucene document field and registers a {@code LuceneIndex} on the {@code GigaMap}.
 * <p>
 * The member value is converted to its string representation. Use {@link #analyzed()} to choose
 * between a tokenized full-text field (default) and an exact-match keyword field.
 *
 * @see org.eclipse.store.gigamap.lucene.LuceneAnnotationHandler
 */
@Retention(RUNTIME)
@Target({FIELD, METHOD})
public @interface FullText
{
	/**
	 * The Lucene document field name. Defaults to the property name (field name, or getter name
	 * with the {@code get}/{@code is} prefix stripped) when empty.
	 *
	 * @return the Lucene field name
	 */
	public String name() default "";

	/**
	 * Whether the value is tokenized for full-text search ({@code true}, the default, produces a
	 * {@code TextField}) or stored as a single exact-match token ({@code false} produces a
	 * {@code StringField}).
	 *
	 * @return true for a tokenized full-text field, false for an exact-match keyword field
	 */
	public boolean analyzed() default true;

	/**
	 * Whether the original value is stored in the index so it can be retrieved from search results.
	 *
	 * @return true to store the field value
	 */
	public boolean store() default true;
}
