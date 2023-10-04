package org.eclipse.store.storage.restadapter.types;

/*-
 * #%L
 * EclipseStore Storage REST Adapter
 * %%
 * Copyright (C) 2023 MicroStream Software
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
import org.eclipse.serializer.persistence.types.PersistenceTypeHandler;

public class ViewerBinaryTypeHandlerBasic<T> extends ViewerBinaryTypeHandlerWrapperAbstract<T>
{
	///////////////////////////////////////////////////////////////////////////
	// constructors //
	/////////////////

	public ViewerBinaryTypeHandlerBasic(final PersistenceTypeHandler<Binary, T> nativeHandler,
			final ViewerBinaryTypeHandlerGeneric genericHandler)
	{
		super(nativeHandler, genericHandler);
	}

	///////////////////////////////////////////////////////////////////////////
	// methods //
	////////////

	@Override
	public ObjectDescription create(final Binary medium, final PersistenceLoadHandler handler)
	{
		final ObjectDescription objectDescription = this.genericHandler.create(medium, handler);
		objectDescription.setPrimitiveInstance(this.nativeHandler.create(medium, handler));

		return objectDescription;
	}

	@SuppressWarnings("unchecked") // safe by logic
	@Override
	public void updateState(final Binary medium, final Object instance, final PersistenceLoadHandler handler)
	{
		this.nativeHandler.updateState(medium, (T)((ObjectDescription)instance).getPrimitiveInstance(), handler);
	}

}
