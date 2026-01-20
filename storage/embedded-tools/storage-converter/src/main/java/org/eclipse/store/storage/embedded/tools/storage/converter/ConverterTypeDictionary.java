package org.eclipse.store.storage.embedded.tools.storage.converter;

/*-
 * #%L
 * EclipseStore Storage Embedded Tools Storage Converter
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

import org.eclipse.serializer.chars.VarString;
import org.eclipse.serializer.collections.BulkList;
import org.eclipse.serializer.collections.EqHashEnum;
import org.eclipse.serializer.collections.types.XImmutableSequence;
import org.eclipse.serializer.persistence.binary.types.BinaryFieldLengthResolver;
import org.eclipse.serializer.persistence.types.PersistenceTypeDefinition;
import org.eclipse.serializer.persistence.types.PersistenceTypeDefinitionCreator;
import org.eclipse.serializer.persistence.types.PersistenceTypeDefinitionMember;
import org.eclipse.serializer.persistence.types.PersistenceTypeDescription;
import org.eclipse.serializer.persistence.types.PersistenceTypeDescriptionMember;
import org.eclipse.serializer.persistence.types.PersistenceTypeDictionaryAssembler;
import org.eclipse.serializer.persistence.types.PersistenceTypeDictionaryParser;
import org.eclipse.serializer.persistence.types.PersistenceTypeDictionaryParser.Default;
import org.eclipse.serializer.persistence.types.PersistenceTypeNameMapper;
import org.eclipse.serializer.persistence.types.PersistenceTypeResolver;
import org.eclipse.serializer.reflect.ClassLoaderProvider;
import org.eclipse.serializer.util.logging.Logging;
import org.slf4j.Logger;

public class ConverterTypeDictionary
{
	private final static Logger logger = Logging.getLogger(BinaryConverter.class);
	private static PersistenceTypeDefinitionCreator typeDefinitionCreator;
	private static PersistenceTypeDictionaryAssembler persistenceTypeDictionaryAssembler;
	
	private final BulkList<PersistenceTypeDescription> dictionaryEntries;
	private long maxTypeId;

	
	public ConverterTypeDictionary(final String sourceTypeDictionary)
	{
		Default parser = PersistenceTypeDictionaryParser.New(
				PersistenceTypeResolver.New(ClassLoaderProvider.System()),
				new BinaryFieldLengthResolver.Default(),
				PersistenceTypeNameMapper.New());
		
		typeDefinitionCreator = PersistenceTypeDefinitionCreator.New();
		persistenceTypeDictionaryAssembler = PersistenceTypeDictionaryAssembler.New();
		
		this.dictionaryEntries = BulkList.New(parser.parseTypeDictionaryEntries(sourceTypeDictionary));
		this.maxTypeId = this.initMaxTypeID();
	}
	
	public BulkList<PersistenceTypeDescription> entries()
	{
		return this.dictionaryEntries;
	}
	
	public long incrementAndGetMaxTypeID()
	{
		return ++this.maxTypeId;
	}
	
	public PersistenceTypeDefinition createTypeDictionaryEntry(
		final long newTypeId,
		final String clazz,
		final XImmutableSequence<? extends PersistenceTypeDefinitionMember> typeDefinitionMembers)
	{
		if(newTypeId == 0)
		{
			throw new RuntimeException("TypeID not initialized!");
		}
		
		EqHashEnum<PersistenceTypeDefinitionMember> members = EqHashEnum.New(PersistenceTypeDescriptionMember.identityHashEqualator());
		for( PersistenceTypeDefinitionMember m : typeDefinitionMembers)
		{
			members.add(m);
		}
			
		PersistenceTypeDefinition typeDefinition = typeDefinitionCreator.createTypeDefinition(
			newTypeId, clazz, clazz, null, members, members);
					
		VarString typeDictionaryString = VarString.New();
		persistenceTypeDictionaryAssembler.assembleTypeDescription(typeDictionaryString, typeDefinition);
		
		logger.debug("Assembled type dictionary entry: {}", typeDictionaryString);
		
		return typeDefinition;
	}
	
	private long initMaxTypeID()
	{
		long maxId = 0;
		
		for(PersistenceTypeDescription entry : this.dictionaryEntries)
		{
			if(entry.typeId() > maxId) maxId = entry.typeId();
		}
		
		return maxId;
	}

	public void add(final PersistenceTypeDefinition typeDefinition)
	{
		this.entries().add(typeDefinition);
	}

	@Override
	public String toString()
	{
		VarString typeDictionaryString = VarString.New();
		for(PersistenceTypeDescription description : this.dictionaryEntries)
		{
			persistenceTypeDictionaryAssembler.assembleTypeDescription(typeDictionaryString, description);
		}
		
		return typeDictionaryString.toString();
	}
}
