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
 * Handles the binary serialization and deserialization of instances of {@link CompositeBitmapIndexHashing}.
 * This class is designed to manage the binary processing for composite bitmap indices using hashing as the indexing mechanism.
 * It extends {@link AbstractBinaryHandlerAbstractCompositeBitmapIndex} with the specific handling logic for {@link CompositeBitmapIndexHashing}.
 */
public class BinaryHandlerCompositeBitmapIndexHashing
extends AbstractBinaryHandlerAbstractCompositeBitmapIndex<CompositeBitmapIndexHashing<?>, Object[], Object>
{
	@SuppressWarnings("all")
	public static final Class<CompositeBitmapIndexHashing<?>> genericType()
	{
		// no idea how to get ".class" to work otherwise in conjunction with generics.
		return (Class)CompositeBitmapIndexHashing.class;
	}
	
	public static BinaryHandlerCompositeBitmapIndexHashing New()
	{
		return new BinaryHandlerCompositeBitmapIndexHashing();
	}
	
	
	
	///////////////////////////////////////////////////////////////////////////
	// constructors //
	/////////////////
	
	BinaryHandlerCompositeBitmapIndexHashing()
	{
		super(genericType());
	}
	
	
	
	///////////////////////////////////////////////////////////////////////////
	// methods //
	////////////
	
	@Override
	public CompositeBitmapIndexHashing<?> create(final Binary data, final PersistenceLoadHandler handler)
	{
		return new CompositeBitmapIndexHashing<>(null, null, null, false);
	}
	
}
