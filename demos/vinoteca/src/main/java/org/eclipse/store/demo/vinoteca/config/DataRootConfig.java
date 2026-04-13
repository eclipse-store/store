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

import org.eclipse.store.demo.vinoteca.model.DataRoot;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Extracts the {@link DataRoot} from the auto-configured {@link EmbeddedStorageManager}.
 * <p>
 * All indices (bitmap, spatial, Lucene) are registered in the {@link DataRoot} constructor
 * and persisted with the GigaMaps. Services access GigaMaps via {@code DataRoot}.
 */
@Configuration
public class DataRootConfig
{
	private static final Logger LOG = LoggerFactory.getLogger(DataRootConfig.class);

	/**
	 * Exposes the persistent {@link DataRoot} as a Spring bean and logs a one-line summary of the
	 * dataset size on application startup.
	 *
	 * @param storageManager the EclipseStore storage manager auto-configured by
	 *                       {@code integrations-spring-boot3}; its
	 *                       {@link EmbeddedStorageManager#root() root} is the {@link DataRoot}
	 *                       declared in {@code application.properties}
	 * @return the loaded data root (newly created on first start, deserialized on subsequent ones)
	 */
	@Bean
	public DataRoot dataRoot(final EmbeddedStorageManager storageManager)
	{
		final DataRoot root = storageManager.root();

		LOG.info(
			"DataRoot retrieved: {} wines, {} wineries, {} customers, {} orders",
			root.getWines().size(),
			root.getWineries().size(),
			root.getCustomers().size(),
			root.getOrders().size()
		);

		return root;
	}
}
