package org.eclipse.store.demo.vinoteca.model;

/*-
 * #%L
 * EclipseStore Demo Vinoteca
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

import java.util.ArrayList;
import java.util.List;

import org.eclipse.store.demo.vinoteca.index.WineDocumentPopulator;
import org.eclipse.store.demo.vinoteca.index.WineIndices;
import org.eclipse.store.demo.vinoteca.index.WineVectorizer;
import org.eclipse.store.demo.vinoteca.index.WineryIndices;
import org.eclipse.store.gigamap.jvector.VectorIndexConfiguration;
import org.eclipse.store.gigamap.jvector.VectorIndices;
import org.eclipse.store.gigamap.jvector.VectorSimilarityFunction;
import org.eclipse.store.gigamap.lucene.DirectoryCreator;
import org.eclipse.store.gigamap.lucene.LuceneContext;
import org.eclipse.store.gigamap.lucene.LuceneIndex;
import org.eclipse.store.gigamap.types.GigaMap;

/**
 * The persistent root of the Vinoteca object graph.
 * <p>
 * EclipseStore is configured (in {@code application.properties}) to use this class as the storage
 * root, meaning a single {@code DataRoot} instance is loaded on startup and re-stored whenever
 * the application persists changes. All domain data is reachable from here:
 * <ul>
 *   <li>{@link #getWines() wines}    — a {@link GigaMap} of {@link Wine} objects with bitmap,
 *       Lucene full-text and JVector vector indices registered;</li>
 *   <li>{@link #getWineries() wineries} — a {@link GigaMap} of {@link Winery} objects with bitmap
 *       and spatial indices registered;</li>
 *   <li>{@link #getCustomers() customers} — a plain {@link List} of {@link Customer} entries;</li>
 *   <li>{@link #getOrders() orders}      — a plain {@link List} of {@link Order} entries.</li>
 * </ul>
 * <p>
 * The default constructor builds a fresh, empty graph with all indices wired up; this is what
 * runs the very first time the application starts (when no storage directory exists yet). On
 * subsequent starts EclipseStore deserializes the existing root instead.
 *
 * @see org.eclipse.store.demo.vinoteca.config.DataRootConfig
 * @see org.eclipse.store.demo.vinoteca.index.WineIndices
 * @see org.eclipse.store.demo.vinoteca.index.WineryIndices
 */
public class DataRoot
{
	private GigaMap<Wine>    wines;
	private GigaMap<Winery>  wineries;
	private List<Customer>   customers;
	private List<Order>      orders;

	/**
	 * Builds a fresh, empty data root with all GigaMap indices configured:
	 * <ul>
	 *   <li>Bitmap indices on wine name/type/grape/vintage/winery/country/region;</li>
	 *   <li>A Lucene full-text index over wine name, tasting notes, aroma and food pairing;</li>
	 *   <li>A JVector vector index ({@code "wine-embeddings"}, 768-dim cosine) backed by a
	 *       LangChain4j Ollama embedding model running locally on
	 *       {@code http://localhost:11434} with model {@code nomic-embed-text};</li>
	 *   <li>Bitmap and spatial indices on wineries.</li>
	 * </ul>
	 * Used only when no persisted root exists yet — subsequent application starts deserialize the
	 * existing root from the storage directory.
	 */
	public DataRoot()
	{
		this.wines = GigaMap.New();
		this.wines.index().bitmap().addAll(
			WineIndices.NAME,
			WineIndices.TYPE,
			WineIndices.GRAPE_VARIETY,
			WineIndices.VINTAGE,
			WineIndices.WINERY_NAME,
			WineIndices.COUNTRY,
			WineIndices.REGION
		);
		this.wines.index().register(LuceneIndex.Category(
			LuceneContext.New(new WineDocumentPopulator())
		));
		final VectorIndices<Wine> vectorIndices = this.wines.index().register(VectorIndices.Category());
		vectorIndices.add("wine-embeddings",
			VectorIndexConfiguration.builder()
				.dimension(768)
				.similarityFunction(VectorSimilarityFunction.COSINE)
				.build(),
			new WineVectorizer("http://localhost:11434", "nomic-embed-text")
		);

		this.wineries = GigaMap.New();
		this.wineries.index().bitmap().addAll(
			WineryIndices.LOCATION,
			WineryIndices.NAME,
			WineryIndices.REGION,
			WineryIndices.COUNTRY
		);

		this.customers = new ArrayList<>();
		this.orders    = new ArrayList<>();
	}

	/** @return the indexed collection of all wines */
	public GigaMap<Wine> getWines()
	{
		return this.wines;
	}

	/** @return the indexed collection of all wineries */
	public GigaMap<Winery> getWineries()
	{
		return this.wineries;
	}

	/** @return the (mutable) list of all customers */
	public List<Customer> getCustomers()
	{
		return this.customers;
	}

	/** @return the (mutable) list of all orders */
	public List<Order> getOrders()
	{
		return this.orders;
	}
}
