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
 * Provides a concrete implementation of {@link AbstractBinaryHandlerAbstractBitmapIndexHashing}
 * tailored specifically to handle binary data operations for types using
 * {@link SubBitmapIndexHashing}. This class facilitates the creation, storage, retrieval,
 * and updates of binary data structures based on the {@link SubBitmapIndexHashing} model.
 * <p>
 * The primary role of this class is to manage the persistence and reconstruction
 * of bitmap indexes that utilize the `SubBitmapIndexHashing` mechanism effectively,
 * adhering to the underlying mechanisms defined in the abstract hierarchy.
 */
public class BinaryHandlerSubBitmapIndexHashing extends AbstractBinaryHandlerAbstractBitmapIndexHashing<SubBitmapIndexHashing<?>, Object>
{
	@SuppressWarnings("all")
	public static final Class<SubBitmapIndexHashing<?>> genericType()
	{
		// no idea how to get ".class" to work otherwise in conjunction with generics.
		return (Class)SubBitmapIndexHashing.class;
	}
	
	
	
	public static BinaryHandlerSubBitmapIndexHashing New()
	{
		return new BinaryHandlerSubBitmapIndexHashing();
	}
	
	
	
	///////////////////////////////////////////////////////////////////////////
	// constructors //
	/////////////////
	
	BinaryHandlerSubBitmapIndexHashing()
	{
		super(genericType());
	}
	
	
	
	///////////////////////////////////////////////////////////////////////////
	// methods //
	////////////
	
	@Override
	public SubBitmapIndexHashing<?> create(final Binary data, final PersistenceLoadHandler handler)
	{
		return new SubBitmapIndexHashing<>(null, null, 0, null, false);
	}
	
}
