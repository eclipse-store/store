package org.eclipse.store.gigamap.types;

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

/**
 * Extension point that lets index types outside the core GigaMap module participate in
 * annotation-driven index generation.
 * <p>
 * The core {@link IndexerGenerator} only knows how to build {@link BitmapIndex bitmap indices}.
 * Integration modules (for example full-text or vector search) ship their own field/type
 * annotations together with a handler implementing this interface. The handler is wired into the
 * generator explicitly via {@link IndexerGenerator#register(GigaIndexAnnotationHandler)} and is then
 * invoked by {@link IndexerGenerator#generateIndices(GigaMap)} after the bitmap indices have been
 * generated. This keeps the core module free of any dependency on the integration modules.
 * <p>
 * A handler is expected to:
 * <ul>
 *   <li>reflect over {@code entityType} to discover the annotations it is responsible for,</li>
 *   <li>register the matching {@link IndexCategory} on the given {@link GigaIndices} (e.g. via
 *       {@link GigaIndices#register(IndexCategory)}),</li>
 *   <li>do nothing when the entity type carries none of the handler's annotations.</li>
 * </ul>
 *
 * @param <E> the entity type
 *
 * @see IndexerGenerator
 * @see GigaIndices
 */
@FunctionalInterface
public interface GigaIndexAnnotationHandler<E>
{
	/**
	 * Inspects the given entity type for the annotations this handler is responsible for and
	 * registers the corresponding index group(s) on the provided {@link GigaIndices}.
	 * <p>
	 * Implementations must be a no-op when {@code entityType} carries none of the handler's
	 * annotations.
	 *
	 * @param entityType the annotated entity type
	 * @param indices    the index manager of the target {@link GigaMap}
	 */
	public void contribute(Class<E> entityType, GigaIndices<E> indices);
}
