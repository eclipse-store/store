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
 * The {@code BinaryHandlerBinaryBitmapIndex} class is a concrete implementation of
 * {@link AbstractBinaryHandlerAbstractBitmapIndexBinary} for handling binary
 * serialization and deserialization of {@link BinaryBitmapIndex} objects.
 * This handler provides the necessary methods for creating, persisting, loading,
 * and updating the state of {@code BinaryBitmapIndex} instances in the binary format.
 */
public class BinaryHandlerBinaryBitmapIndex extends AbstractBinaryHandlerAbstractBitmapIndexBinary<BinaryBitmapIndex<?>>
{
	@SuppressWarnings("all")
	public static final Class<BinaryBitmapIndex<?>> genericType()
	{
		// no idea how to get ".class" to work otherwise in conjunction with generics.
		return (Class)BinaryBitmapIndex.class;
	}
	
	
	
	public static BinaryHandlerBinaryBitmapIndex New()
	{
		return new BinaryHandlerBinaryBitmapIndex();
	}
	
	
	
	///////////////////////////////////////////////////////////////////////////
	// constructors //
	/////////////////
	
	BinaryHandlerBinaryBitmapIndex()
	{
		super(genericType());
	}
	
	
	
	///////////////////////////////////////////////////////////////////////////
	// methods //
	////////////
	
	@Override
	public BinaryBitmapIndex<?> create(final Binary data, final PersistenceLoadHandler handler)
	{
		return new BinaryBitmapIndex<>(null, null, null, false);
	}
	
}
