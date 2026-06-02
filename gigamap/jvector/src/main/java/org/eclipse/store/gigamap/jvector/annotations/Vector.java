package org.eclipse.store.gigamap.jvector.annotations;

/*-
 * #%L
 * EclipseStore GigaMap JVector
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

import org.eclipse.store.gigamap.jvector.VectorSimilarityFunction;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Marks a {@code float[]} field or no-argument getter as the embedding vector of an entity, to be
 * indexed in a vector (HNSW / approximate nearest neighbor) index.
 * <p>
 * An entity type carrying a {@code @Vector} member can be wired into annotation-based index
 * generation via
 * {@code IndexerGenerator.AnnotationBased(EntityType.class).register(VectorAnnotationHandler.New()).generateIndices(map)}.
 * The vector is read from the entity on demand (embedded mode), so it is not stored a second time
 * by the index.
 *
 * @see org.eclipse.store.gigamap.jvector.VectorAnnotationHandler
 */
@Retention(RUNTIME)
@Target({FIELD, METHOD})
public @interface Vector
{
	/**
	 * The index name. Defaults to the property name (field name, or getter name with the
	 * {@code get}/{@code is} prefix stripped) when empty.
	 *
	 * @return the index name
	 */
	public String name() default "";

	/**
	 * The vector dimension. Must be a positive value matching the length of the {@code float[]}
	 * produced by the annotated member.
	 *
	 * @return the vector dimension
	 */
	public int dimension();

	/**
	 * The similarity function used for nearest-neighbor search.
	 *
	 * @return the similarity function
	 */
	public VectorSimilarityFunction similarity() default VectorSimilarityFunction.COSINE;

	/**
	 * Whether the index should be stored on disk. When {@code true}, the handler must be created
	 * with an index directory (see {@code VectorAnnotationHandler.New(Path)}).
	 *
	 * @return true to store the index on disk
	 */
	public boolean onDisk() default false;
}
