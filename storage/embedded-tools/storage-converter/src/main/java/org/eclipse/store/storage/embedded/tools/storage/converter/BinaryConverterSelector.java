package org.eclipse.store.storage.embedded.tools.storage.converter;

/*-
 * #%L
 * EclipseStore Storage Embedded Tools Storage Converter
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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.HashMap;

public class BinaryConverterSelector
{
	private final HashMap<Long, BinaryConverter> converters;
	private final ConverterTypeDictionary converterTypeDictionary;
	
	public BinaryConverterSelector(final ConverterTypeDictionary typeDictionary)
	{
		this.converters = new HashMap<>();
		this.converterTypeDictionary = typeDictionary;
	}

	public void initConverter(final String binaryConverterClassName)
	{
		MethodHandles.Lookup publicLookup = MethodHandles.publicLookup();
		MethodType mt = MethodType.methodType(void.class, ConverterTypeDictionary.class);
		
		try
		{
			Class<?> clazz = Class.forName(binaryConverterClassName);
			MethodHandle ch = publicLookup.findConstructor(clazz, mt);
			
			BinaryConverter converter = (BinaryConverter)ch.invoke(this.converterTypeDictionary);
			this.match(converter);
			if(converter.requiresTypeDictionaryUpdate())
			{
				this.converterTypeDictionary.add(converter.getTypeDefinition());
			}
			
		}
		catch(Throwable e)
		{
			throw new RuntimeException("Failed to initialize converter: " + binaryConverterClassName, e);
		}
	}
	
	private void match(final BinaryConverter converter)
	{
		this.converterTypeDictionary.entries().forEach(
			e ->  { if(converter.matches(e)) {
				this.converters.put(e.typeId(), converter);
			}});
	}

	public BinaryConverter get(final long tid)
	{
		return this.converters.get(tid);
	}
}
