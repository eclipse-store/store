package org.eclipse.store.demo.vinoteca.config;

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

import org.eclipse.store.demo.vinoteca.index.WineDocumentPopulator;
import org.eclipse.store.demo.vinoteca.index.WineIndices;
import org.eclipse.store.demo.vinoteca.index.WineryIndices;
import org.eclipse.store.demo.vinoteca.index.WineryLocationIndex;
import org.eclipse.store.demo.vinoteca.model.DataRoot;
import org.eclipse.store.demo.vinoteca.model.Wine;
import org.eclipse.store.demo.vinoteca.model.Winery;
import org.eclipse.store.gigamap.lucene.DirectoryCreator;
import org.eclipse.store.gigamap.lucene.LuceneContext;
import org.eclipse.store.gigamap.lucene.LuceneIndex;
import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures GigaMap instances and their indices.
 * <p>
 * The {@link EmbeddedStorageManager} is auto-configured by the EclipseStore Spring Boot
 * integration using {@code org.eclipse.store.*} properties. This configuration extracts
 * the {@link DataRoot} from the storage manager and registers transient indices on the
 * GigaMaps (indices are not persisted and must be re-registered on each startup).
 */
@Configuration
public class GigaMapConfig
{
	private static final Logger LOG = LoggerFactory.getLogger(GigaMapConfig.class);

	@Bean
	public DataRoot dataRoot(final EmbeddedStorageManager storageManager)
	{
		final DataRoot root = (DataRoot) storageManager.root();

		// Register transient indices on the loaded GigaMaps
		registerWineIndices(root.getWines());
		registerWineryIndices(root.getWineries());

		LOG.info(
			"DataRoot loaded: {} wines, {} wineries, {} customers, {} orders",
			root.getWines().size(),
			root.getWineries().size(),
			root.getCustomers().size(),
			root.getOrders().size()
		);

		return root;
	}

	@Bean
	public GigaMap<Wine> wineGigaMap(final DataRoot dataRoot)
	{
		return dataRoot.getWines();
	}

	@Bean
	public GigaMap<Winery> wineryGigaMap(final DataRoot dataRoot)
	{
		return dataRoot.getWineries();
	}

	@Bean
	public WineryLocationIndex wineryLocationIndex()
	{
		return WineryIndices.LOCATION;
	}

	@Bean
	public LuceneIndex<Wine> wineLuceneIndex(final GigaMap<Wine> wineGigaMap)
	{
		final LuceneContext<Wine> luceneContext = LuceneContext.New(
			DirectoryCreator.ByteBuffers(),
			new WineDocumentPopulator()
		);
		return wineGigaMap.index().register(LuceneIndex.Category(luceneContext));
	}

	private static void registerWineIndices(final GigaMap<Wine> gigaMap)
	{
		gigaMap.index().bitmap().addAll(
			WineIndices.NAME,
			WineIndices.TYPE,
			WineIndices.GRAPE_VARIETY,
			WineIndices.WINERY_NAME,
			WineIndices.COUNTRY,
			WineIndices.REGION
		);
	}

	private static void registerWineryIndices(final GigaMap<Winery> gigaMap)
	{
		gigaMap.index().bitmap().addAll(
			WineryIndices.LOCATION,
			WineryIndices.NAME,
			WineryIndices.REGION,
			WineryIndices.COUNTRY
		);
	}
}
