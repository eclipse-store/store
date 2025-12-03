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

import java.nio.ByteBuffer;
import java.util.List;

import org.eclipse.serializer.collections.types.XGettingSequence;
import org.eclipse.serializer.collections.types.XImmutableSequence;
import org.eclipse.serializer.memory.XMemory;
import org.eclipse.serializer.persistence.binary.types.AbstractBinaryHandlerCustom;
import org.eclipse.serializer.persistence.types.PersistenceTypeDefinition;
import org.eclipse.serializer.persistence.types.PersistenceTypeDefinitionMember;
import org.eclipse.serializer.persistence.types.PersistenceTypeDescription;
import org.eclipse.serializer.persistence.types.PersistenceTypeDescriptionMember;
import org.eclipse.serializer.util.logging.Logging;
import org.slf4j.Logger;

public class BinaryConverterBitmapLevel2 implements BinaryConverter
{
	private final static Logger logger = Logging.getLogger(BinaryConverter.class);
	
	private static final int BINARY_SIZE_ADJUSTMENT = 20;
	private static final int LIST_SIZE_ADJUSTMENT = 24;
	private static final int OUTPUT_BINARY_FORMAT_VERSION = 2;
	
	private final static String clazz = "org.eclipse.store.gigamap.types.BitmapLevel2";
	private final static List<String> fields = List.of("data");
	
	private final static XImmutableSequence<? extends PersistenceTypeDefinitionMember> newTypeDefinitionMembers = AbstractBinaryHandlerCustom.CustomFields(
		AbstractBinaryHandlerCustom.CustomField(int.class, "version"),
		AbstractBinaryHandlerCustom.bytes("data"));
	
	private final long newTypeId;
	private final PersistenceTypeDefinition newTypeDefinition;

    public BinaryConverterBitmapLevel2(ConverterTypeDictionary converterTypeDictionary) {
        this.newTypeId = converterTypeDictionary.incrementAndGetMaxTypeID();
		this.newTypeDefinition = converterTypeDictionary.createTypeDictionaryEntry(
			this.newTypeId,
			clazz,
			BinaryConverterBitmapLevel2.newTypeDefinitionMembers);
	}
	
	@Override
	public PersistenceTypeDefinition getTypeDefinition() {
		return this.newTypeDefinition;
	}
	
	@Override
	public boolean requiresTypeDictionaryUpdate() {
		return true;
	}
	
	@Override
	public ByteBuffer convert(final ByteBuffer bufferIn) {
		
		int pos = bufferIn.position();
		int size = bufferIn.limit() - bufferIn.position();
		
		long binarySize = bufferIn.getLong(pos);
		long binaryTid = bufferIn.getLong(pos + 8);
		long binaryOid = bufferIn.getLong(pos + 16);
							
		long newSize = size + BINARY_SIZE_ADJUSTMENT;
		long newListSize = size - LIST_SIZE_ADJUSTMENT;

        ByteBuffer converted = XMemory.allocateDirectNative(newSize);
		
		logger.debug("Converting object: binaryTid {}, binaryOid {} , binarySize {} to binaryTid {}, binaryOid {} , binarySize {}",
				binaryTid, binaryOid, binarySize,
				this.newTypeId, binaryOid, newSize);
		
		converted.putLong(newSize);		// 0
		converted.putLong(this.newTypeId);	// 8
		converted.putLong(binaryOid);   //16
		
		converted.putInt(OUTPUT_BINARY_FORMAT_VERSION);	//24
		
		converted.putLong(newListSize); //28
		converted.putLong(newListSize);//36
		
		converted.put(44, bufferIn, pos + LIST_SIZE_ADJUSTMENT, size - LIST_SIZE_ADJUSTMENT); //44
		converted.limit(44 + size - LIST_SIZE_ADJUSTMENT);
		converted.position(0);
				
		return converted;
	}
	
	@Override
	public boolean matches(final PersistenceTypeDescription e) {
		if(clazz.equals(e.typeName()))
		{
			//existing type definition != new type definition
			if(PersistenceTypeDescription.equalDescription(e, this.newTypeDefinition)) {
				return false;
			}
			
			//must contain "data" field
			XGettingSequence<? extends PersistenceTypeDescriptionMember> members = e.allMembers();
			for(int i = 0; i < e.allMembers().size(); i++) {
				if(!BinaryConverterBitmapLevel2.fields.contains(members.at(i).identifier())) return false;
			}
			
			return true;
		}
		return false;
	}
}
