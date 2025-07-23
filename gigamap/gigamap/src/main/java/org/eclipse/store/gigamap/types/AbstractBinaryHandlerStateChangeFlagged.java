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

import org.eclipse.serializer.collections.types.XGettingSequence;
import org.eclipse.serializer.persistence.binary.types.AbstractBinaryHandlerCustom;
import org.eclipse.serializer.persistence.binary.types.Binary;
import org.eclipse.serializer.persistence.types.PersistenceStoreHandler;
import org.eclipse.serializer.persistence.types.PersistenceTypeDefinitionMember;


/**
 * AbstractBinaryHandlerStateChangeFlagged is an abstract base class that provides an implementation
 * framework for binary persistence handlers of objects that track their state changes. This class is
 * designed to only store objects if they are new or have undergone changes, leveraging the state
 * information provided by the object itself.
 * <p>
 * Subclasses of this class must implement the specific logic for how the instance should be stored
 * and any additional handling logic for specific types of child references or fields, as appropriate
 * for the type managed by the handler.
 *
 * @param <I> The type of objects handled by this binary persistence handler, which must extend
 *            AbstractStateChangeFlagged to ensure compatibility with state-change functionality.
 */
public abstract class AbstractBinaryHandlerStateChangeFlagged<I extends AbstractStateChangeFlagged>
extends AbstractBinaryHandlerCustom<I>
{
	
	///////////////////////////////////////////////////////////////////////////
	// constructors //
	/////////////////
	
	protected AbstractBinaryHandlerStateChangeFlagged(
		final Class<I>                                                    type   ,
		final XGettingSequence<? extends PersistenceTypeDefinitionMember> members
	)
	{
		super(type, members);
	}
	
	
	
	///////////////////////////////////////////////////////////////////////////
	// methods //
	////////////
	
	@Override
	public void store(
		final Binary                          data    ,
		final I                               instance,
		final long                            objectId,
		final PersistenceStoreHandler<Binary> handler
	)
	{
		// instance only needs to be stored if it is new or changed, decided by the instance's class.
		if(instance.isInstanceNewOrChanged())
		{
			// HOW the instance is stored is a concern of the type handler.
			this.internalStore(data, instance, objectId, handler);
		}
		
		// handle storing children, which is specific to the class' logic.
		instance.storeChildren(handler);
	}
	
	protected abstract void internalStore(
		Binary                          data    ,
		I                               instance,
		long                            objectId,
		PersistenceStoreHandler<Binary> handler
	);
			
}
