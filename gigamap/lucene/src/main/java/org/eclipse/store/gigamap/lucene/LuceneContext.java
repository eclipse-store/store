package org.eclipse.store.gigamap.lucene;

/*-
 * #%L
 * EclipseStore GigaMap Lucene
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

import org.apache.lucene.document.Document;
import org.apache.lucene.store.Directory;

import java.nio.file.Path;

import static org.eclipse.serializer.util.X.notNull;

/**
 * LuceneContext is an interface that provides a context for facilitating operations
 * within a Lucene index. It acts as a wrapper to combine the creation and
 * configuration of essential Lucene components such as directories, analyzers,
 * and document population.
 * <p>
 * This interface consists of methods to:
 * - Create and manage Lucene Directory instances via a DirectoryCreator.
 * - Create and manage Lucene Analyzer instances via an AnalyzerCreator.
 * - Populate Lucene documents with custom data via a DocumentPopulator.
 * <p>
 * The LuceneContext also provides static factory methods to construct instances
 * of its default implementation with varying levels of customization.
 * These methods allow users to initialize a new context by specifying custom
 * DirectoryCreator, AnalyzerCreator, or DocumentPopulator implementations,
 * or by relying on default configurations where applicable.
 * <p>
 * The {@code Default<E>} implementation of this interface provides a concrete
 * means to instantiate and use a LuceneContext.
 *
 * @param <E> the type of entities to be handled by the associated DocumentPopulator.
 */
public interface LuceneContext<E>
{
	/**
	 * Provides an instance of {@link DirectoryCreator} responsible for creating
	 * specific types of {@link Directory} instances. The exact implementation
	 * of the {@link DirectoryCreator} determines the type of {@link Directory}
	 * that will be created.
	 *
	 * @return an instance of {@link DirectoryCreator} that encapsulates the logic
	 *         for creating a specific type of {@link Directory}.
	 */
	public DirectoryCreator directoryCreator();
	
	/**
	 * Provides an instance of {@link AnalyzerCreator} responsible for creating
	 * instances of analyzers used for text analysis in Lucene. The exact type
	 * of analyzer created is defined by the specific implementation of the
	 * {@link AnalyzerCreator}.
	 *
	 * @return an instance of {@link AnalyzerCreator} that encapsulates the logic
	 *         for creating analyzers.
	 */
	public AnalyzerCreator analyzerCreator();
	
	/**
	 * Provides an instance of {@link DocumentPopulator} responsible for populating Lucene {@link Document}
	 * objects with data from entities of type {@code E}. This enables consistent indexing and searching
	 * operations for the specific entity type.
	 *
	 * @return an instance of {@link DocumentPopulator} that defines how to map entity data to Lucene documents
	 */
	public DocumentPopulator<E> documentPopulator();

    /**
     * Determines whether the operations in this context are automatically committed
     * to the underlying storage or require explicit commits.
     *
     * @return true if the context is set to automatically commit changes;
     *         false if manual commits are required.
     */
    public default boolean autoCommit()
    {
        return true;
    }

	
	/**
	 * Creates a new instance of {@link LuceneContext} for a specific type of entity.
	 * This method automatically handles the creation of a directory using the MMap implementation
	 * and a standard analyzer for text analysis, based on the provided directory path
	 * and the document populator implementation.
	 *
	 * @param <E> the type of entity to be indexed and searched using this {@link LuceneContext}
	 * @param directoryPath the path to the directory where the Lucene index will be stored
	 * @param documentPopulator an implementation of {@link DocumentPopulator} responsible for
	 *                           populating Lucene documents with data from the specified entity type
	 * @return an instance of {@link LuceneContext} configured with the given directory path,
	 *         standard analyzer, and document populator
	 */
	public static <E> LuceneContext<E> New(
		final Path                 directoryPath    ,
		final DocumentPopulator<E> documentPopulator
	)
	{
		return New(
			DirectoryCreator.MMap(directoryPath),
			AnalyzerCreator.Standard()          ,
			documentPopulator
		);
	}
	
	/**
	 * Creates a new instance of {@link LuceneContext} for handling Lucene operations.
	 * This method uses a provided {@link DirectoryCreator} and {@link DocumentPopulator},
	 * while internally relying on a standard analyzer.
	 *
	 * @param <E> the type of entity to be indexed and searched with the resulting {@link LuceneContext}
	 * @param directoryCreator an implementation of {@link DirectoryCreator} responsible for
	 *                         creating a directory where the Lucene index will be stored
	 * @param documentPopulator an implementation of {@link DocumentPopulator} responsible for
	 *                          mapping entity data into Lucene {@link Document} objects
	 * @return an instance of {@link LuceneContext} configured with the given directory creator
	 *         and document populator, and a standard analyzer for text processing
	 */
	public static <E> LuceneContext<E> New(
		final DirectoryCreator     directoryCreator ,
		final DocumentPopulator<E> documentPopulator
	)
	{
		return New(
			directoryCreator          ,
			AnalyzerCreator.Standard(),
			documentPopulator
		);
	}
	
	/**
	 * Creates a new instance of {@link LuceneContext} for handling Lucene operations.
	 * This method uses the provided {@link DirectoryCreator}, {@link AnalyzerCreator},
	 * and {@link DocumentPopulator} to configure the resulting {@link LuceneContext}.
	 *
	 * @param <E> the type of entity to be indexed and searched with the resulting {@link LuceneContext}
	 * @param directoryCreator an implementation of {@link DirectoryCreator} responsible for
	 *                         creating a directory where the Lucene index will be stored
	 * @param analyzerCreator an implementation of {@link AnalyzerCreator} responsible for
	 *                        creating analyzers used for text processing in Lucene
	 * @param documentPopulator an implementation of {@link DocumentPopulator} responsible for
	 *                          mapping*/
	public static <E> LuceneContext<E> New(
		final DirectoryCreator     directoryCreator ,
		final AnalyzerCreator      analyzerCreator  ,
		final DocumentPopulator<E> documentPopulator
	)
	{
		return new Default<>(
			notNull(directoryCreator ),
			notNull(analyzerCreator  ),
			notNull(documentPopulator)
		);
	}
	
	
	
	public class Default<E> implements LuceneContext<E>
	{
		private final DirectoryCreator     directoryCreator;
		private final AnalyzerCreator      analyzerCreator;
		private final DocumentPopulator<E> documentPopulator;
		
		Default(
			final DirectoryCreator     directoryCreator ,
			final AnalyzerCreator      analyzerCreator  ,
			final DocumentPopulator<E> documentPopulator
		)
		{
			super();
			this.directoryCreator  = directoryCreator ;
			this.analyzerCreator   = analyzerCreator  ;
			this.documentPopulator = documentPopulator;
		}

		@Override
		public DirectoryCreator directoryCreator()
		{
			return this.directoryCreator;
		}

		@Override
		public AnalyzerCreator analyzerCreator()
		{
			return this.analyzerCreator;
		}

		@Override
		public DocumentPopulator<E> documentPopulator()
		{
			return this.documentPopulator;
		}
		
	}
	
}
