
package org.eclipse.store.integrations.cdi.types.config;

/*-
 * #%L
 * EclipseStore Integrations CDI 4
 * %%
 * Copyright (C) 2023 - 2024 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import org.eclipse.microprofile.config.spi.Converter;
import org.eclipse.store.storage.types.StorageManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


/**
 * A Config converter to {@link StorageManager}
 */
public class StorageManagerConverter implements Converter<StorageManager>
{
	private static final Logger LOGGER = LoggerFactory.getLogger(StorageManagerConverter.class);

	private static final Map<String, StorageManager> MAP = new ConcurrentHashMap<>();

	@Override
	public StorageManager convert(final String value) throws IllegalArgumentException, NullPointerException
	{
		return MAP.computeIfAbsent(value, this::createStorageManager);
	}

	private StorageManager createStorageManager(final String value)
	{
		LOGGER.info("Loading configuration to start the class StorageManager from the key: " + value);
		return new StorageManagerProxy(value);
	}


}
