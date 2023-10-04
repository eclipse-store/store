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

import org.eclipse.serializer.persistence.types.Persistence;

public class ObjectReferenceWrapper
{
	///////////////////////////////////////////////////////////////////////////
	// instance fields //
	////////////////////

	private long objectId;


	///////////////////////////////////////////////////////////////////////////
	// constructors //
	/////////////////

	public ObjectReferenceWrapper(final long objectId)
	{
		super();
		this.setObjectId(objectId);
	}


	///////////////////////////////////////////////////////////////////////////
	// methods //
	////////////

	public long getObjectId()
	{
		return this.objectId;
	}

	public void setObjectId(final long objectId)
	{
		this.objectId = objectId;
	}

	public boolean isValidObjectReference()
	{
		return Persistence.IdType.OID.isInRange(this.objectId);
	}

	public  boolean isValidConstantReference()
	{
		return Persistence.IdType.CID.isInRange(this.objectId);
	}
}
