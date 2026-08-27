package org.eclipse.store.gigamap.lucene;

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

import org.eclipse.store.gigamap.lucene.annotations.FullText;
import org.eclipse.store.gigamap.types.GigaIndexAnnotationHandler;
import org.eclipse.store.gigamap.types.GigaIndices;

import java.nio.file.Path;

import static org.eclipse.serializer.util.X.notNull;

/**
 * A {@link GigaIndexAnnotationHandler} that contributes a {@code LuceneIndex} for entity types
 * carrying {@link FullText} annotated members.
 * <p>
 * Register it with annotation-based index generation to wire up full-text search declaratively:
 * <pre>{@code
 * IndexerGenerator.AnnotationBased(Article.class)
 *     .register(LuceneAnnotationHandler.New())
 *     .generateIndices(gigaMap);
 * }</pre>
 * When the entity type carries no {@link FullText} member, {@link #contribute(Class, GigaIndices)}
 * is a no-op.
 * <p>
 * By default the Lucene data is stored inside the index (in-graph). Use {@link #New(DirectoryCreator)}
 * or {@link #New(Path)} to store the Lucene index in a directory instead.
 *
 * @param <E> the entity type
 *
 * @see FullText
 */
public final class LuceneAnnotationHandler<E> implements GigaIndexAnnotationHandler<E>
{
	/**
	 * Creates a handler that stores the Lucene index data inside the GigaMap object graph.
	 *
	 * @param <E> the entity type
	 * @return a new handler
	 */
	public static <E> LuceneAnnotationHandler<E> New()
	{
		return new LuceneAnnotationHandler<>(null);
	}

	/**
	 * Creates a handler that stores the Lucene index using the given {@link DirectoryCreator}.
	 *
	 * @param <E>              the entity type
	 * @param directoryCreator the directory creator for the Lucene index
	 * @return a new handler
	 */
	public static <E> LuceneAnnotationHandler<E> New(final DirectoryCreator directoryCreator)
	{
		return new LuceneAnnotationHandler<>(notNull(directoryCreator));
	}

	/**
	 * Creates a handler that stores the Lucene index in the given directory (memory-mapped).
	 *
	 * @param <E>           the entity type
	 * @param directoryPath the directory for the Lucene index
	 * @return a new handler
	 */
	public static <E> LuceneAnnotationHandler<E> New(final Path directoryPath)
	{
		return new LuceneAnnotationHandler<>(DirectoryCreator.MMap(notNull(directoryPath)));
	}


	private final DirectoryCreator directoryCreator;

	private LuceneAnnotationHandler(final DirectoryCreator directoryCreator)
	{
		super();
		this.directoryCreator = directoryCreator;
	}

	@Override
	public void contribute(final Class<E> entityType, final GigaIndices<E> indices)
	{
		if(!AnnotationDocumentPopulator.hasFullTextMembers(entityType))
		{
			return;
		}

		final AnnotationDocumentPopulator<E> populator = new AnnotationDocumentPopulator<>(entityType);

		final LuceneContext<E> context = this.directoryCreator == null
			? LuceneContext.New(populator)
			: LuceneContext.New(this.directoryCreator, populator)
		;

		indices.register(LuceneIndex.Category(context));
	}

}
