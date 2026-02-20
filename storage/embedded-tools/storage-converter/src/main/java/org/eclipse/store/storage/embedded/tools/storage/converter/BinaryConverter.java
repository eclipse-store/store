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

import java.nio.ByteBuffer;

import org.eclipse.serializer.persistence.types.PersistenceTypeDefinition;
import org.eclipse.serializer.persistence.types.PersistenceTypeDescription;

/**
 * The Binary Converter converters the input binary data to a new binary format.
 */
public interface BinaryConverter
{
	/**
	 * Convert the input binary data.
	 * 
	 * @param bufferIn binary input data.
	 * @return ByteBuffer containing the converted binary data.
	 */
	ByteBuffer convert(ByteBuffer bufferIn);
	
	/**
	 * Returns a new typeDefinition if the converted
	 * binary data requires an update of the type definition.
	 * The new TypeDefinition will be added to the output
	 * storage type-dictionary.
	 * 
	 * @return a PersistenceTypeDefinition
	 */
	public PersistenceTypeDefinition getTypeDefinition();
	
	/**
	 * Returns true if the handler requires the type dictionary to be updated.
	 * 
	 * @return true if the converter updates type dictionary.
	 */
	boolean requiresTypeDictionaryUpdate();
	
	/**
	 * Returns true if the converter can process
	 * the provided type.
	 * 
	 * @param e PersistenceTypeDescription to check if the type can be processed by the converter.
	 * @return true if converter is can process the types' data.
	 */
	boolean matches(PersistenceTypeDescription e);
}
