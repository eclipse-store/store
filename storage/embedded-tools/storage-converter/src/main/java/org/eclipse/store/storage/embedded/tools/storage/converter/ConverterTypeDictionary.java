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

/**
 * In-memory representation of the source storage's persistence type dictionary as used by the
 * {@link StorageConverter}.
 * <p>
 * The class parses an existing type dictionary, exposes its entries to {@link BinaryConverter}s for matching,
 * and lets converters allocate fresh type ids and append new {@link PersistenceTypeDefinition}s for the
 * converted binary formats. {@link #toString()} re-assembles the (possibly extended) dictionary back into the
 * textual form expected by the target storage.
 */
public class ConverterTypeDictionary
{
	private final static Logger logger = Logging.getLogger(BinaryConverter.class);
	private static PersistenceTypeDefinitionCreator typeDefinitionCreator;
	private static PersistenceTypeDictionaryAssembler persistenceTypeDictionaryAssembler;

	private final BulkList<PersistenceTypeDescription> dictionaryEntries;
	private long maxTypeId;


	/**
	 * Parses the supplied textual type dictionary and seeds the running maximum type id from it.
	 *
	 * @param sourceTypeDictionary the source storage's persistence type dictionary as a string.
	 */
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
	
	/**
	 * Returns the live, mutable list of dictionary entries. Converters use this list to look up source type
	 * descriptions and may append new {@link PersistenceTypeDefinition}s via {@link #add(PersistenceTypeDefinition)}.
	 *
	 * @return the dictionary entries.
	 */
	public BulkList<PersistenceTypeDescription> entries()
	{
		return this.dictionaryEntries;
	}

	/**
	 * Allocates a fresh type id by incrementing the running maximum type id and returning the new value.
	 *
	 * @return a previously unused type id.
	 */
	public long incrementAndGetMaxTypeID()
	{
		return ++this.maxTypeId;
	}

	/**
	 * Creates a new {@link PersistenceTypeDefinition} for the given class with the supplied member layout.
	 * <p>
	 * The created definition is not automatically added to the dictionary; converters typically pass it back
	 * via {@link BinaryConverter#getTypeDefinition()} so that {@link BinaryConverterSelector} can decide
	 * whether to register it.
	 *
	 * @param newTypeId             the type id to assign to the new definition; must not be {@code 0}.
	 * @param clazz                 the fully qualified class name the definition stands for.
	 * @param typeDefinitionMembers the members making up the new binary layout, in declaration order.
	 *
	 * @return the newly assembled {@link PersistenceTypeDefinition}.
	 *
	 * @throws RuntimeException if {@code newTypeId} is {@code 0}.
	 */
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

	/**
	 * Appends the given {@link PersistenceTypeDefinition} to the dictionary entries so that subsequent calls
	 * to {@link #toString()} include it in the assembled output.
	 *
	 * @param typeDefinition the definition to append.
	 */
	public void add(final PersistenceTypeDefinition typeDefinition)
	{
		this.entries().add(typeDefinition);
	}

	/**
	 * Assembles the current dictionary entries back into the textual form expected by the storage layer.
	 *
	 * @return the assembled type dictionary string.
	 */
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
