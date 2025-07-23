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

import org.eclipse.serializer.persistence.binary.types.BinaryTypeHandler;
import org.eclipse.serializer.persistence.types.Storer;
import org.eclipse.serializer.persistence.types.Unpersistable;


/**
 * GigaLevel1 represents a generic container that manages an array of entities.
 * It extends {@link AbstractStateChangeFlagged} to incorporate state change tracking
 * capabilities but functions as a leaf node in the state hierarchy, meaning it
 * does not track state changes for children since it does not conceptually
 * contain any.
 * <p>
 * This class provides functionality to initialize and manage a typed array of
 * entities. It ensures safe type handling during runtime via instantiation-specific
 * operations despite type erasure.
 *
 * @param <E> The type of elements stored in this container.
 */
public final class GigaLevel1<E> extends AbstractStateChangeFlagged implements Unpersistable
{
	///////////////////////////////////////////////////////////////////////////
	// static methods //
	///////////////////
	
	static BinaryTypeHandler<GigaLevel1<?>> provideTypeHandler()
	{
		return BinaryHandlerGigaLevel1.New();
	}
	
	@SuppressWarnings("unchecked")
	private E[] createEntitiesArray(final int length)
	{
		return (E[])new Object[length];
	}
	
	
	
	///////////////////////////////////////////////////////////////////////////
	// instance fields //
	////////////////////
	
	E[] entities;
		
	
	
	///////////////////////////////////////////////////////////////////////////
	// constructors //
	/////////////////

	GigaLevel1(final int length, final boolean newInstance)
	{
		super(newInstance);
		this.entities = this.createEntitiesArray(length);
	}
	
	@Override
	protected void storeChangedChildren(final Storer storer)
	{
		// GigaLevel1 may never be marked as having children changed since is it a leaf instance. Not perfectly clean.
		throw new UnsupportedOperationException();
	}
	
	@Override
	protected void clearChildrenStateChangeMarkers()
	{
		// no-op since there are no state-change-marked children.
	}
	
}
