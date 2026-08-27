package org.eclipse.store.gigamap.annotations;

/*-
 * #%L
 * EclipseStore GigaMap
 * %%
 * Copyright (C) 2023 - 2025 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import org.eclipse.store.gigamap.types.Indexer;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Annotation to indicate that a field or no-argument getter should be indexed. This can be used to
 * specify metadata for the indexing process, including the name of the index, the kind of index to
 * generate and the creator class responsible for building the index.
 */
@Retention(RUNTIME)
@Target({FIELD, METHOD})
public @interface Index
{
	/**
	 * Specify a custom name for the index.
	 *
	 * @return custom name
	 */
	public String name() default "";

	/**
	 * Force optional creation of a binary index, only works for natural number types and String.
	 * <p>
	 * This is a backward-compatible shortcut for {@link #kind()} {@code = }{@link IndexKind#BINARY}.
	 * When {@link #kind()} is set to anything other than {@link IndexKind#AUTO}, that value takes
	 * precedence over this flag.
	 *
	 * @return true if binary index creation should be forced, false otherwise
	 */
	public boolean binary() default false;

	/**
	 * Select which kind of bitmap index to generate. Defaults to {@link IndexKind#AUTO}, which picks
	 * a suitable index based on the field type (honoring {@link #binary()} and {@link Unique} for
	 * backward compatibility).
	 *
	 * @return the index kind
	 */
	public IndexKind kind() default IndexKind.AUTO;

	/**
	 * Provide a custom creator for the resulting index
	 *
	 * @return a custom creator
	 */
	public Class<? extends Indexer.Creator> creator() default Indexer.Creator.Dummy.class;
}
