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
 * A handler for managing binary serialization and deserialization of the {@link CompositeBitmapIndexBinary} type.
 * This class extends functionality from {@link AbstractBinaryHandlerAbstractCompositeBitmapIndex}
 * to specifically work with composite bitmap indices represented in binary format.
 * <p>
 * It provides capabilities to create, store, update, and manage references for the
 * {@link CompositeBitmapIndexBinary} instances in binary persistence systems.
 */
public class BinaryHandlerCompositeBitmapIndexBinary
	extends AbstractBinaryHandlerAbstractCompositeBitmapIndex<CompositeBitmapIndexBinary<?>, long[], Long>
{
	@SuppressWarnings("all")
	public static final Class<CompositeBitmapIndexBinary<?>> genericType()
	{
		// no idea how to get ".class" to work otherwise in conjunction with generics.
		return (Class)CompositeBitmapIndexBinary.class;
	}
	
	public static BinaryHandlerCompositeBitmapIndexBinary New()
	{
		return new BinaryHandlerCompositeBitmapIndexBinary();
	}
	
	
	
	///////////////////////////////////////////////////////////////////////////
	// constructors //
	/////////////////
	
	BinaryHandlerCompositeBitmapIndexBinary()
	{
		super(genericType());
	}
	
	
	
	///////////////////////////////////////////////////////////////////////////
	// methods //
	////////////
	
	@Override
	public CompositeBitmapIndexBinary<?> create(final Binary data, final PersistenceLoadHandler handler)
	{
		return new CompositeBitmapIndexBinary<>(null, null, null, false);
	}
	
}
