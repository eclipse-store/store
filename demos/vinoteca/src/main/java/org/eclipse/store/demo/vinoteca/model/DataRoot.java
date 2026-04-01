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

public class DataRoot
{
	private GigaMap<Wine>    wines;
	private GigaMap<Winery>  wineries;
	private List<Customer>   customers;
	private List<Order>      orders;

	public DataRoot()
	{
		this.wines = GigaMap.New();
		this.wines.index().bitmap().addAll(
			WineIndices.NAME,
			WineIndices.TYPE,
			WineIndices.GRAPE_VARIETY,
			WineIndices.WINERY_NAME,
			WineIndices.COUNTRY,
			WineIndices.REGION
		);
		this.wines.index().register(LuceneIndex.Category(
			LuceneContext.New(DirectoryCreator.ByteBuffers(), new WineDocumentPopulator())
		));
		final VectorIndices<Wine> vectorIndices = this.wines.index().register(VectorIndices.Category());
		vectorIndices.add("wine-embeddings",
			VectorIndexConfiguration.builder()
				.dimension(384)
				.similarityFunction(VectorSimilarityFunction.COSINE)
				.build(),
			new WineVectorizer("http://localhost:11434", "all-minilm")
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

	public GigaMap<Wine> getWines()
	{
		return this.wines;
	}

	public GigaMap<Winery> getWineries()
	{
		return this.wineries;
	}

	public List<Customer> getCustomers()
	{
		return this.customers;
	}

	public List<Order> getOrders()
	{
		return this.orders;
	}
}
