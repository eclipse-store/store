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
	 * <p>
	 * If <code>null</code> is returned, the data will be stored inside the index.
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
     * Determines whether index mutations are committed eagerly, immediately at mutation
     * time, or only when explicitly requested.
     * <p>
     * The effect of this flag depends on the directory type (see {@link #directoryCreator()}):
     * <table border="1">
     *   <caption>{@code autoCommit} &times; directory type</caption>
     *   <tr><th></th><th>{@code autoCommit == true} (default)</th><th>{@code autoCommit == false}</th></tr>
     *   <tr>
     *     <td><b>Embedded</b> (directory creator is {@code null}; index data lives in the
     *         persisted object graph)</td>
     *     <td>Each mutation commits into the in-memory graph; the data reaches disk only on
     *         {@code GigaMap.store()}. Already consistent at store boundaries.</td>
     *     <td>The writer is flushed and committed once, exactly at the {@code GigaMap.store()}
     *         boundary, instead of after every mutation.</td>
     *   </tr>
     *   <tr>
     *     <td><b>External</b> (e.g. {@link DirectoryCreator#MMap(java.nio.file.Path)};
     *         index data lives on disk, outside the GigaMap storage)</td>
     *     <td>Each mutation commits to disk immediately, decoupled from
     *         {@code GigaMap.store()}: a mutation not followed by a store (or a crash in
     *         between) leaves the on-disk index diverged from the persisted entities.</td>
     *     <td>The writer is flushed and committed at the {@code GigaMap.store()} boundary,
     *         giving store-aligned durability. A manual {@link LuceneIndex#commit()} is also
     *         honored.</td>
     *   </tr>
     * </table>
     * <p>
     * Note: an external index is a separate persistence target from the GigaMap storage.
     * Coupling its commit to {@code store()} aligns the two commits temporally but is not a
     * single atomic transaction across both targets; a crash mid-{@code store()} can still
     * diverge. An embedded (graph) index has no such gap, as it is part of the persisted graph.
     *
     * @return {@code true} (the default) to commit eagerly at mutation time;
     *         {@code false} to couple commits to {@code GigaMap.store()} / explicit
     *         {@link LuceneIndex#commit()}.
     */
    public default boolean autoCommit()
    {
        return true;
    }


	/**
	 * Creates a new instance of {@link LuceneContext} for a specific type of entity.
	 * The data will be stored inside the index.
	 *
	 * @param <E> the type of entity to be indexed and searched using this {@link LuceneContext}
	 * @param documentPopulator an implementation of {@link DocumentPopulator} responsible for
	 *                           populating Lucene documents with data from the specified entity type
	 * @return an instance of {@link LuceneContext} configured with a standard analyzer and document populator
	 */
	public static <E> LuceneContext<E> New(final DocumentPopulator<E> documentPopulator)
	{
		return New(
			null,
			AnalyzerCreator.Standard(),
			documentPopulator
		);
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
	 *                         creating a directory where the Lucene index will be stored,
	 *                         or <code>null</code> if the data should be stored inside the index
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
		return New(
			        directoryCreator  ,
			notNull(analyzerCreator  ),
			notNull(documentPopulator),
			true
		);
	}

	/**
	 * Creates a new instance of {@link LuceneContext} for handling Lucene operations, with
	 * explicit control over the commit behavior.
	 *
	 * @param <E> the type of entity to be indexed and searched with the resulting {@link LuceneContext}
	 * @param directoryCreator an implementation of {@link DirectoryCreator} responsible for
	 *                         creating a directory where the Lucene index will be stored,
	 *                         or <code>null</code> if the data should be stored inside the index
	 * @param analyzerCreator an implementation of {@link AnalyzerCreator} responsible for
	 *                        creating analyzers used for text processing in Lucene
	 * @param documentPopulator an implementation of {@link DocumentPopulator} responsible for
	 *                          mapping entity data into Lucene {@link Document} objects
	 * @param autoCommit see {@link LuceneContext#autoCommit()}; pass {@code false} to couple
	 *                   commits to {@code GigaMap.store()} / explicit {@link LuceneIndex#commit()}
	 *                   instead of committing eagerly at mutation time
	 * @return an instance of {@link LuceneContext} configured with the given parameters
	 */
	public static <E> LuceneContext<E> New(
		final DirectoryCreator     directoryCreator ,
		final AnalyzerCreator      analyzerCreator  ,
		final DocumentPopulator<E> documentPopulator,
		final boolean              autoCommit
	)
	{
		return new Default<>(
			        directoryCreator  ,
			notNull(analyzerCreator  ),
			notNull(documentPopulator),
			autoCommit
		);
	}



	public class Default<E> implements LuceneContext<E>
	{
		private final DirectoryCreator     directoryCreator;
		private final AnalyzerCreator      analyzerCreator;
		private final DocumentPopulator<E> documentPopulator;
		// Inverted on purpose: a field added to this persisted type defaults to false when an
		// older store (without the field) is loaded. Storing manualCommit (not autoCommit) makes
		// that false-default mean autoCommit()==true, preserving the legacy behavior on reload.
		private final boolean              manualCommit;

		Default(
			final DirectoryCreator     directoryCreator ,
			final AnalyzerCreator      analyzerCreator  ,
			final DocumentPopulator<E> documentPopulator
		)
		{
			this(directoryCreator, analyzerCreator, documentPopulator, true);
		}

		Default(
			final DirectoryCreator     directoryCreator ,
			final AnalyzerCreator      analyzerCreator  ,
			final DocumentPopulator<E> documentPopulator,
			final boolean              autoCommit
		)
		{
			super();
			this.directoryCreator  = directoryCreator ;
			this.analyzerCreator   = analyzerCreator  ;
			this.documentPopulator = documentPopulator;
			this.manualCommit      = !autoCommit      ;
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

		@Override
		public boolean autoCommit()
		{
			return !this.manualCommit;
		}

	}
	
}
