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
 * A handler implementation for managing binary serialization and deserialization
 * of {@link SubBitmapIndexBinary} instances. This class extends
 * {@link AbstractBinaryHandlerAbstractBitmapIndexBinary} tailored for handling
 * the {@link SubBitmapIndexBinary} type.
 * <p>
 * This class specializes the persistence operations such as storing,
 * loading, and updating state for {@link SubBitmapIndexBinary} objects,
 * integrating with binary persistence workflow.
 * <p>
 * This handler ensures the appropriate back-references and transient state
 * initialization for {@link SubBitmapIndexBinary}.
 */
public class BinaryHandlerSubBitmapIndexBinary extends AbstractBinaryHandlerAbstractBitmapIndexBinary<SubBitmapIndexBinary<?>>
{
	@SuppressWarnings("all")
	public static final Class<SubBitmapIndexBinary<?>> genericType()
	{
		// no idea how to get ".class" to work otherwise in conjunction with generics.
		return (Class)SubBitmapIndexBinary.class;
	}
	
	
	
	public static BinaryHandlerSubBitmapIndexBinary New()
	{
		return new BinaryHandlerSubBitmapIndexBinary();
	}
	
	
	
	///////////////////////////////////////////////////////////////////////////
	// constructors //
	/////////////////
	
	BinaryHandlerSubBitmapIndexBinary()
	{
		super(genericType());
	}
	
	
	
	///////////////////////////////////////////////////////////////////////////
	// methods //
	////////////
	
	@Override
	public SubBitmapIndexBinary<?> create(final Binary data, final PersistenceLoadHandler handler)
	{
		return new SubBitmapIndexBinary<>(null, null, 0, false);
	}
	
}
