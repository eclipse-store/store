package org.eclipse.store.gigamap.types;

/*-
 * #%L
 * EclipseStore GigaMap
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

import org.eclipse.serializer.persistence.binary.types.Binary;
import org.eclipse.serializer.persistence.types.PersistenceLoadHandler;


/**
 * A binary handler implementation for managing instances of {@link HashingBitmapIndex}.
 * This class provides functionality for serializing and deserializing binary data
 * related to {@link HashingBitmapIndex}, enabling efficient persistence and reconstruction
 * of indexing structures based on hashing and bitmap mechanisms.
 * <p>
 * Extends {@link AbstractBinaryHandlerAbstractBitmapIndexHashing} to reuse mechanisms
 * specific to handling hashed bitmap indices and integrates these functionalities
 * for binary data storage and retrieval.
 * <p>
 * The primary responsibilities of this handler include:
 * <ul>
 * <li>Creating new instances of {@link HashingBitmapIndex} during deserialization.</li>
 * <li>Managing binary structure updates and persistence processes.</li>
 * </ul>
 */
public class BinaryHandlerHashingBitmapIndex extends AbstractBinaryHandlerAbstractBitmapIndexHashing<HashingBitmapIndex<?, ?>, Object>
{
	@SuppressWarnings("all")
	public static final Class<HashingBitmapIndex<?, ?>> genericType()
	{
		// no idea how to get ".class" to work otherwise in conjunction with generics.
		return (Class)HashingBitmapIndex.class;
	}
	
	
	
	public static BinaryHandlerHashingBitmapIndex New()
	{
		return new BinaryHandlerHashingBitmapIndex();
	}
	
	
	
	///////////////////////////////////////////////////////////////////////////
	// constructors //
	/////////////////
	
	BinaryHandlerHashingBitmapIndex()
	{
		super(genericType());
	}
	
	
	
	///////////////////////////////////////////////////////////////////////////
	// methods //
	////////////
	
	@Override
	public HashingBitmapIndex<?, ?> create(final Binary data, final PersistenceLoadHandler handler)
	{
		return new HashingBitmapIndex<>(null, null, null, null, false);
	}
	
}
