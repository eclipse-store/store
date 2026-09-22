package org.eclipse.store.storage.restadapter.types;

/*-
 * #%L
 * EclipseStore Storage REST Adapter
 * %%
 * Copyright (C) 2023 - 2026 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import static org.eclipse.serializer.persistence.types.PersistenceTypeDescriptionMemberFieldValueStruct.NULL_MARKER_ABSENT;
import static org.eclipse.serializer.persistence.types.PersistenceTypeDescriptionMemberFieldValueStruct.NULL_MARKER_LENGTH;

import org.eclipse.serializer.persistence.binary.types.Binary;
import org.eclipse.serializer.persistence.types.PersistenceTypeDefinitionMemberField;
import org.eclipse.serializer.persistence.types.PersistenceTypeDefinitionMemberFieldValueStruct;

/**
 * Reads a field whose value is written into its owner rather than referenced by an object id.
 * <p>
 * The slot is a null marker byte followed by the inlined type's own values, so it is read as the values
 * of that type, in an array, in the order the type describes them. An absent value reads as
 * {@literal null}, which is what the marker states.
 */
public class ValueReaderValueStruct implements ValueReader
{
	///////////////////////////////////////////////////////////////////////////
	// instance fields //
	////////////////////

	private final ValueReader[] memberReaders;
	private final long          structLength ;



	///////////////////////////////////////////////////////////////////////////
	// constructors //
	/////////////////

	public ValueReaderValueStruct(final PersistenceTypeDefinitionMemberFieldValueStruct member)
	{
		super();

		this.memberReaders = new ValueReader[member.members().intSize()];

		int i = 0;
		for(final PersistenceTypeDefinitionMemberField nested : member.members())
		{
			this.memberReaders[i++] = ValueReader.deriveValueReader(nested);
		}

		this.structLength = member.persistentMinimumLength();
	}



	///////////////////////////////////////////////////////////////////////////
	// methods //
	////////////

	@Override
	public Object readValue(final Binary binary, final long offset)
	{
		if(binary.read_byte(offset) == NULL_MARKER_ABSENT)
		{
			return null;
		}

		final Object[] values = new Object[this.memberReaders.length];

		long memberOffset = offset + NULL_MARKER_LENGTH;
		for(int i = 0; i < this.memberReaders.length; i++)
		{
			values[i]     = this.memberReaders[i].readValue(binary, memberOffset);
			memberOffset += this.memberReaders[i].getBinarySize(binary, memberOffset);
		}

		return values;
	}

	@Override
	public long getBinarySize(final Binary binary, final long offset)
	{
		// fixed by the described layout, whether or not the value is present
		return this.structLength;
	}

}
