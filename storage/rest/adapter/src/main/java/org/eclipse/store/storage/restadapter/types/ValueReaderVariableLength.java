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
import org.eclipse.serializer.persistence.types.PersistenceTypeDefinitionMember;

public abstract class ValueReaderVariableLength implements ValueReader
{
	///////////////////////////////////////////////////////////////////////////
	// instance fields //
	////////////////////

	protected final PersistenceTypeDefinitionMember typeDefinition;

	///////////////////////////////////////////////////////////////////////////
	// constructors //
	/////////////////

	public ValueReaderVariableLength(final PersistenceTypeDefinitionMember typeDefinition)
	{
		super();
		this.typeDefinition = typeDefinition;
	}

	@Override
	public long getBinarySize(final Binary binary, final long offset)
	{
		return binary.getBinaryListTotalByteLength(offset);
	}

	@Override
	public long getVariableLength(final Binary binary, final long offset)
	{
		return binary.getBinaryListElementCountUnvalidating(offset);
	}

}
