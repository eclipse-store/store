package org.eclipse.store.storage.restadapter.types;

/*-
 * #%L
 * EclipseStore Storage REST Adapter
 * %%
 * Copyright (C) 2023 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import org.eclipse.serializer.collections.EqHashTable;

import java.util.ServiceLoader;

public interface StorageViewDataConverterRegistry extends StorageViewDataConverterProvider
{
	@Override
	public StorageViewDataConverter getConverter(String format);

	/**
	 * Registers a new data converter.
	 *
	 * @param converter the converter
	 * @param format the handled format
	 * @return true if successful registered, otherwise false
	 */
	public boolean addConverter(StorageViewDataConverter converter, String format);
	
	
	public static StorageViewDataConverterRegistry New()
	{
		final StorageViewDataConverterRegistry registry = new StorageViewDataConverterRegistry.Default();
		
		final ServiceLoader<StorageViewDataConverter> serviceLoader =
			ServiceLoader.load(StorageViewDataConverter.class);

		for (final StorageViewDataConverter converter : serviceLoader)
		{
			for (final String  format : converter.getFormatStrings())
			{
				registry.addConverter(converter, format);
			}
		}
		
		return registry;
	}
	

	
	public static class Default implements StorageViewDataConverterRegistry
	{
		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////

		private final EqHashTable<String, StorageViewDataConverter> converters;


		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////

		Default()
		{
			super();
			this.converters = EqHashTable.New();
		}


		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////

		@Override
		public StorageViewDataConverter getConverter(
			final String format
		)
		{
			return this.converters.get(format);
		}

		@Override
		public boolean addConverter(
			final StorageViewDataConverter converter,
			final String                   format
		)
		{
			return this.converters.add(format, converter);
		}
	}
}
