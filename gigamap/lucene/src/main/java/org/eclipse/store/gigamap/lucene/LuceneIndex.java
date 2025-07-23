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

import org.eclipse.store.gigamap.types.CustomConstraints;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.gigamap.types.IndexCategory;
import org.eclipse.store.gigamap.types.IndexGroup;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.LongField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.queryparser.flexible.core.QueryNodeException;
import org.apache.lucene.queryparser.flexible.standard.StandardQueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.eclipse.serializer.exceptions.IORuntimeException;
import org.eclipse.serializer.math.XMath;

import java.io.Closeable;
import java.io.IOException;
import java.util.*;

import static org.eclipse.serializer.util.X.notNull;


/**
 * Represents a Lucene-based index for managing and querying entities of type E.
 * This interface extends both the {@link IndexGroup} interface and the {@link Closeable} interface,
 * providing methods for querying entities using Lucene queries and text search, as well as lifecycle
 * management methods for closing the index.
 *
 * @param <E> The type of entity managed by the index.
 */
public interface LuceneIndex<E> extends IndexGroup<E>, Closeable
{
	/**
	 * A functional interface for consuming search results retrieved from a Lucene index.
	 * <p>
	 * The {@code SearchResultAcceptor} interface provides a method for accepting individual
	 * search results from a query execution. It is used to process the ID, entity,
	 * and associated relevance score of each search result.
	 *
	 * @param <E> the type of the entity associated with the search result
	 */
	public interface SearchResultAcceptor<E>
	{
		public void accept(long entityId, E entity, float score);
	}
	
	/**
	 * Executes a query against the Lucene index and processes the results using the provided
	 * {@code SearchResultAcceptor}. This method allows defining custom logic for handling each
	 * search result, including its entity ID, associated entity, and relevance score.
	 *
	 * @param <A> the type of the {@code SearchResultAcceptor} that will process the search results
	 * @param query the {@code Query} object representing the search criteria to be executed
	 * @param searchResultAcceptor an instance of {@code SearchResultAcceptor} to handle each search result
	 *                             returned by the query execution
	 * @return the {@code SearchResultAcceptor} instance provided as a parameter, potentially modified
	 *         during the result handling process
	 */
	public <A extends SearchResultAcceptor<? super E>> A query(Query query, A searchResultAcceptor);
	
	/**
	 * Executes a query against the Lucene index and processes the results using the provided
	 * {@code SearchResultAcceptor}. This method allows defining custom logic for handling each
	 * search result, including its entity ID, associated entity, and relevance score, with a limit
	 * on the maximum number of results.
	 *
	 * @param <A> the type of the {@code SearchResultAcceptor} that will process the search results
	 * @param query the {@code Query} object representing the search criteria to be executed
	 * @param maxResults the maximum number of search results that should be processed
	 * @param searchResultAcceptor an instance of {@code SearchResultAcceptor} to handle each search result
	 *                             returned by the query execution
	 * @return the {@code SearchResultAcceptor} instance provided as a parameter, potentially modified
	 *         during the result handling process
	 */
	public <A extends SearchResultAcceptor<? super E>> A query(Query query, int maxResults, A searchResultAcceptor);
	
	/**
	 * Executes a query against the Lucene index using the specified query text and processes the results
	 * using the provided {@code SearchResultAcceptor}. This method allows defining custom logic for
	 * handling each search result, including its entity ID, associated entity, and relevance score.
	 *
	 * @param <A> the type of the {@code SearchResultAcceptor} that will process the search results
	 * @param queryText the text representation of the query to be executed
	 * @param searchResultAcceptor an instance of {@code SearchResultAcceptor} to handle each search result
	 *                              returned by the query execution
	 * @return the {@code SearchResultAcceptor} instance provided as a parameter, potentially modified
	 *         during the result handling process
	 */
	public <A extends SearchResultAcceptor<? super E>> A query(String queryText, A searchResultAcceptor);
	
	/**
	 * Executes a query against the Lucene index using the specified query text and processes
	 * the results using the provided {@code SearchResultAcceptor}. This method allows defining
	 * custom logic for handling each search result, including its entity ID, associated entity,
	 * and relevance score, with a limit on the maximum number of results.
	 *
	 * @param <A> the type of the {@code SearchResultAcceptor} that will process the search results
	 * @param queryText the text representation of the query to be executed
	 * @param maxResults the maximum number of search results that should be processed
	 * @param searchResultAcceptor an instance of {@code SearchResultAcceptor} to handle each search result
	 *                              returned by the query execution
	 * @return the {@code SearchResultAcceptor} instance provided as a parameter, potentially modified
	 *         during the result handling process
	 */
	public <A extends SearchResultAcceptor<? super E>> A query(String queryText, int maxResults, A searchResultAcceptor);
	
	/**
	 * Executes a query against the Lucene index and returns a list of entities
	 * that match the provided search criteria.
	 *
	 * @param query the {@code Query} object representing the search criteria to be executed
	 * @return a {@code List} of entities of type {@code E} that match the query
	 */
	public default List<E> query(final Query query)
	{
		final List<E> result = new ArrayList<>();
		this.query(query, (entityId, entity, score) -> result.add(entity));
		return result;
	}
	
	/**
	 * Executes a query against the Lucene index and returns a list of entities
	 * that match the provided search criteria, with a limit on the maximum number of results.
	 *
	 * @param query the {@code Query} object representing the search criteria to be executed
	 * @param maxResults the maximum number of search results to be returned
	 * @return a {@code List} of entities of type {@code E} that match the query
	 */
	public default List<E> query(final Query query, final int maxResults)
	{
		final List<E> result = new ArrayList<>();
		this.query(query, maxResults, (entityId, entity, score) -> result.add(entity));
		return result;
	}
	
	/**
	 * Executes a query against the Lucene index using the specified query text
	 * and returns a list of entities that match the query.
	 *
	 * @param queryText the text representation of the query to be executed
	 * @return a list of entities of type {@code E} that match the query
	 */
	public default List<E> query(final String queryText)
	{
		final List<E> result = new ArrayList<>();
		this.query(queryText, (entityId, entity, score) -> result.add(entity));
		return result;
	}
	
	/**
	 * Executes a query against the Lucene index using the specified query text and returns
	 * a list of entities matching the query, with a limit on the maximum number of results.
	 *
	 * @param queryText the text representation of the query to be executed
	 * @param maxResults the maximum number of search results to be returned
	 * @return a list of entities of type {@code E} that match the query
	 */
	public default List<E> query(final String queryText, final int maxResults)
	{
		final List<E> result = new ArrayList<>();
		this.query(queryText, maxResults, (entityId, entity, score) -> result.add(entity));
		return result;
	}
	
	/**
	 * Closes this index and all resources associated with it.
	 * <p>
	 * Note that this index can be re-used after it was closed.
	 * Internally it uses lazy initialization, which will be triggered again after it was closed. 
	 */
	@Override
	public void close();
	
	
	public interface Internal<E> extends LuceneIndex<E>, IndexGroup.Internal<E>
	{
		// typing interface
	}
	
	
	public class Default<E> implements Internal<E>
	{
		private final static String ENTITY_ID_FIELD = "_id_";
		
		private static Query queryFor(final long entityId)
		{
			return LongField.newExactQuery(ENTITY_ID_FIELD, entityId);
		}
		
		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////
		
		private final GigaMap<E>          gigaMap;
		private final LuceneContext<E>    context;

		private transient Analyzer        analyzer;
		private transient Directory       directory;
		private transient IndexWriter     writer;
		private transient DirectoryReader reader;
		private transient IndexSearcher   searcher;
		
		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////
		
		protected Default(
			final GigaMap<E>       gigaMap,
			final LuceneContext<E> context
		)
		{
			super();
			this.gigaMap = gigaMap;
			this.context = context;
		}
		
		
		
		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////
		
		@Override
		public GigaMap<E> parentMap()
		{
			return this.gigaMap;
		}
		
		@Override
		public void internalAdd(final long entityId, final E entity)
		{
			try
			{
				synchronized(this.gigaMap)
				{
					this.lazyInit();
				
					final Document document = new Document();
					document.add(new LongField(ENTITY_ID_FIELD, entityId, Store.YES));
					this.context.documentPopulator().populate(document, entity);
					
					this.writer.addDocument(document);
				}
			}
			catch(final IOException e)
			{
				throw new IORuntimeException(e);
			}
		}
		
		@Override
		public void internalAddAll(final long firstEntityId, final Iterable<? extends E> entities)
		{
			try
			{
				synchronized(this.gigaMap)
				{
					this.lazyInit();
				
					final List<Document> documents       = new ArrayList<>();
					long                 currentEntityId = firstEntityId;
					
					for(final E entity : entities)
					{
						final Document document = new Document();
						document.add(new LongField(ENTITY_ID_FIELD, currentEntityId++, Store.YES));
						this.context.documentPopulator().populate(document, entity);
						documents.add(document);
					}
					
					this.writer.addDocuments(documents);
				}
			}
			catch(final IOException e)
			{
				throw new IORuntimeException(e);
			}
		}
		
		@Override
		public void internalAddAll(final long firstEntityId, final E[] entities)
		{
			this.internalAddAll(firstEntityId, Arrays.asList(entities));
		}
		
		@Override
		public void internalRemove(final long entityId, final E entity)
		{
			try
			{
				synchronized(this.gigaMap)
				{
					this.lazyInit();
				
					this.writer.deleteDocuments(queryFor(entityId));
				}
			}
			catch(final IOException e)
			{
				throw new IORuntimeException(e);
			}
		}
		
		@Override
		public void internalRemoveAll()
		{
			try
			{
				synchronized(this.gigaMap)
				{
					this.lazyInit();
				
					this.writer.deleteAll();
				}
			}
			catch(final IOException e)
			{
				throw new IORuntimeException(e);
			}
		}
		
		@Override
		public void internalPrepareIndicesUpdate(final E replacedEntity)
		{
			// no-op
		}
		
		@Override
		public void internalFinishIndicesUpdate()
		{
			// no-op
		}
		
		@Override
		public void internalUpdateIndices(
			final long                         entityId         ,
			final E                            replacedEntity   ,
			final E                            entity           ,
			final CustomConstraints<? super E> customConstraints
		)
		{
			try
			{
				synchronized(this.gigaMap)
				{
					this.lazyInit();
					
					final Document document = new Document();
					document.add(new LongField(ENTITY_ID_FIELD, entityId, Store.YES));
					this.context.documentPopulator().populate(document, entity);
					this.writer.updateDocuments(queryFor(entityId), List.of(document));
				}
			}
			catch(final IOException e)
			{
				throw new IORuntimeException(e);
			}
		}
		
		@Override
		public <A extends SearchResultAcceptor<? super E>> A query(final String queryText, final A searchResultAcceptor)
		{
			return this.query(queryText, this.defaultMaxResults(), searchResultAcceptor);
		}
		
		@Override
		public <A extends SearchResultAcceptor<? super E>> A query(
			final String queryText           ,
			final int    maxResults          ,
			final A      searchResultAcceptor
		)
		{
			if(maxResults <= 0)
			{
				return searchResultAcceptor;
			}
			
			try
			{
				synchronized(this.gigaMap)
				{
					this.lazyInit();
					
					final StandardQueryParser queryParser = new StandardQueryParser(this.analyzer);
					queryParser.setAllowLeadingWildcard(true);
					final Query query = queryParser.parse(queryText, ENTITY_ID_FIELD);
					this.syncInternalQuery(query, maxResults, searchResultAcceptor);
				}
			}
			catch(final IOException e)
			{
				throw new IORuntimeException(e);
			}
			catch(final QueryNodeException e)
			{
				throw new RuntimeException(e);
			}
			
			return searchResultAcceptor;
		}
		
		@Override
		public <A extends SearchResultAcceptor<? super E>> A query(final Query query, final A searchResultAcceptor)
		{
			return this.query(query, this.defaultMaxResults(), searchResultAcceptor);
		}
		
		@Override
		public <A extends SearchResultAcceptor<? super E>> A query(
			final Query query               ,
			final int   maxResults          ,
			final A     searchResultAcceptor
		)
		{
			try
			{
				synchronized(this.gigaMap)
				{
					this.lazyInit();
				
					this.syncInternalQuery(query, maxResults, searchResultAcceptor);
				}
			}
			catch(final IOException e)
			{
				throw new IORuntimeException(e);
			}
			
			return searchResultAcceptor;
		}



		private <A extends SearchResultAcceptor<? super E>> void syncInternalQuery(
			final Query query               ,
			final int   maxResults          ,
			final A     searchResultAcceptor
		)
		throws IOException
		{
			final StoredFields storedFields = this.searcher.storedFields();
			final Set<String>  idOnly       = new HashSet<>(List.of(ENTITY_ID_FIELD));
			final TopDocs      topDocs      = this.searcher.search(query, maxResults);
			for(final ScoreDoc scoreDoc : topDocs.scoreDocs)
			{
				final long entityId = storedFields
					.document(scoreDoc.doc, idOnly)
					.getField(ENTITY_ID_FIELD).numericValue().longValue()
				;
				final E entity;
				if((entity = this.gigaMap.get(entityId)) != null)
				{
					searchResultAcceptor.accept(entityId, entity, scoreDoc.score);
				}
			}
		}
		
		@Override
		public void clearStateChangeMarkers()
		{
			// no-op
		}
		
		private int defaultMaxResults()
		{
			return XMath.cap_int(this.gigaMap.size());
		}

		private void lazyInit() throws IOException
		{
			if(this.directory == null)
			{
				this.searcher              = new IndexSearcher(
					this.reader            = DirectoryReader.open(
						this.writer        = new IndexWriter(
							this.directory = this.context.directoryCreator().createDirectory(),
							new IndexWriterConfig(
								this.analyzer = this.context.analyzerCreator().createAnalyzer()
							)
						)
					)
				);
			}
			else
			{
				final DirectoryReader newReader = DirectoryReader.openIfChanged(this.reader);
				if(newReader != null && newReader != this.reader)
				{
					this.reader.close();
					this.searcher = new IndexSearcher(
						this.reader = newReader
					);
				}
			}
		}
		
		@Override
		public void close()
		{
			synchronized(this.gigaMap)
			{
				final Closeable[] closeables = {this.analyzer, this.writer, this.reader, this.directory};
				for(@SuppressWarnings("resource") final Closeable closeable : closeables)
				{
					if(closeable != null)
					{
						try
						{
							closeable.close();
						}
						catch(final IOException e)
						{
							throw new IORuntimeException(e);
						}
					}
				}
				
				this.analyzer  = null;
				this.directory = null;
				this.writer    = null;
				this.reader    = null;
				this.searcher  = null;
			}
		}
		
	}
	
	/**
	 * Creates a new instance of {@code LuceneIndex.Category} for the specified {@code LuceneContext}.
	 * This method facilitates the creation of a category associated with a given context.
	 *
	 * @param <E> the type of the entities managed by this category
	 * @param context the {@code LuceneContext} instance to be associated with the category
	 * @return a new {@code LuceneIndex.Category} instance bound to the specified context
	 */
	public static <E> Category<E> Category(final LuceneContext<E> context)
	{
		return new Category.Default<>(
			notNull(context)
		);
	}
	
	
	public interface Category<E> extends IndexCategory<E, LuceneIndex<E>>
	{
		@Override
		public Class<LuceneIndex<E>> indexType();
		
		
		public class Default<E> implements Category<E>
		{
			private final LuceneContext<E> context;
			
			
			///////////////////////////////////////////////////////////////////////////
			// constructors //
			/////////////////
			
			Default(final LuceneContext<E> context)
			{
				super();
				this.context = context;
			}
			
			
			
			///////////////////////////////////////////////////////////////////////////
			// methods //
			////////////
			
			@SuppressWarnings({ "unchecked", "rawtypes" })
			@Override
			public Class<LuceneIndex<E>> indexType()
			{
				return (Class)LuceneIndex.class;
			}
			
			@Override
			public Internal<E> createIndexGroup(final GigaMap<E> gigaMap)
			{
				return new LuceneIndex.Default<>(gigaMap, this.context);
			}
			
		}
		
	}
	
}
