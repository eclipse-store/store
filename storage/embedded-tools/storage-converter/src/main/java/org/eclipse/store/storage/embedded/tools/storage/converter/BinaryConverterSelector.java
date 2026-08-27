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

/**
 * Resolves the {@link BinaryConverter} (if any) responsible for transforming the binary data of a given
 * source type id during a {@link StorageConverter} run.
 * <p>
 * Converters are registered by class name via {@link #initConverter(String)}: the named class is instantiated
 * (it must expose a public constructor taking a single {@link ConverterTypeDictionary}) and matched against
 * every type description in the dictionary. For each match, the converter is associated with the source type
 * id; if the converter requires a type dictionary update its new {@link org.eclipse.serializer.persistence.types.PersistenceTypeDefinition}
 * is also added to the dictionary so the target storage can read the converted blobs back.
 */
public class BinaryConverterSelector
{
	private final HashMap<Long, BinaryConverter> converters;
	private final ConverterTypeDictionary converterTypeDictionary;

	/**
	 * Creates a new {@link BinaryConverterSelector} backed by the supplied {@link ConverterTypeDictionary}.
	 *
	 * @param typeDictionary the type dictionary used both to match candidate types and to register new
	 *        converted-type definitions.
	 */
	public BinaryConverterSelector(final ConverterTypeDictionary typeDictionary)
	{
		this.converters = new HashMap<>();
		this.converterTypeDictionary = typeDictionary;
	}

	/**
	 * Loads, instantiates and registers the {@link BinaryConverter} identified by the given fully qualified
	 * class name.
	 * <p>
	 * The class is required to declare a public constructor taking a single {@link ConverterTypeDictionary}.
	 * After instantiation, the converter is matched against every entry of the type dictionary and bound to
	 * the source type ids it accepts; if the converter reports {@link BinaryConverter#requiresTypeDictionaryUpdate()},
	 * its new type definition is added to the dictionary.
	 *
	 * @param binaryConverterClassName the fully qualified class name of a {@link BinaryConverter} implementation.
	 *
	 * @throws RuntimeException if the converter cannot be loaded, instantiated, or invoked.
	 */
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

	/**
	 * Returns the {@link BinaryConverter} that has been bound to the given source type id, or {@code null}
	 * if no registered converter matched this type.
	 *
	 * @param tid the source type id.
	 *
	 * @return the {@link BinaryConverter} responsible for {@code tid}, or {@code null} if none.
	 */
	public BinaryConverter get(final long tid)
	{
		return this.converters.get(tid);
	}
}
