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

import org.eclipse.serializer.collections.types.XGettingSequence;
import org.eclipse.serializer.persistence.binary.types.Binary;
import org.eclipse.serializer.persistence.types.PersistenceTypeDefinitionMember;
import org.eclipse.serializer.persistence.types.PersistenceTypeDefinitionMemberFieldGenericComplex;
import org.eclipse.serializer.persistence.types.PersistenceTypeDescriptionMemberFieldGeneric;

public class ValueReaderArrayOfLists extends ValueReaderVariableLength
{
	///////////////////////////////////////////////////////////////////////////
	// instance fields //
	////////////////////

	private final ValueReader readers[];

	///////////////////////////////////////////////////////////////////////////
	// constructors //
	/////////////////

	public ValueReaderArrayOfLists(final PersistenceTypeDefinitionMember typeDefinition)
	{
		super(typeDefinition);

		final PersistenceTypeDefinitionMemberFieldGenericComplex.Default instance = (PersistenceTypeDefinitionMemberFieldGenericComplex.Default) typeDefinition;
		final XGettingSequence<PersistenceTypeDescriptionMemberFieldGeneric> instanceMembers = instance.members();

		this.readers = new ValueReader[instanceMembers.intSize()];
		for(int i = 0; i< instanceMembers.intSize(); i++)
		{
			this.readers[i] = ValueReader.deriveValueReader((PersistenceTypeDefinitionMember) instanceMembers.at(i));
		}
	}

	///////////////////////////////////////////////////////////////////////////
	// methods //
	////////////

	@Override
	public Object readValue(final Binary binary, final long offset)
	{

		long listOffset = Binary.toBinaryListElementsOffset(offset);
		final int elementCount = (int) binary.getBinaryListElementCountUnvalidating(offset);

		final Object lists[] = new Object[elementCount];

		for(int j = 0; j < elementCount; j++)
		{
			final Object[] objectValues = new Object[this.readers.length];
			for(int i = 0; i < this.readers.length; i++)
			{
				objectValues[i] = this.readers[i].readValue(binary, listOffset);
				final long size = this.readers[i].getBinarySize(binary, listOffset);
				listOffset += size;
			}

			lists[j] = objectValues;
		}

		return lists;
	}
}
