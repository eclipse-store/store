package org.eclipse.store.storage.types;

import java.io.IOException;

/*-
 * #%L
 * EclipseStore Storage
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

import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import org.eclipse.serializer.collections.types.XGettingTable;
import org.eclipse.serializer.memory.XMemory;
import org.eclipse.serializer.persistence.binary.types.Binary;
import org.eclipse.serializer.persistence.binary.types.BinaryReferenceTraverser;
import org.eclipse.serializer.persistence.types.Persistence;
import org.eclipse.serializer.persistence.types.PersistenceTypeDefinition;
import org.eclipse.serializer.reference.Swizzling;
import org.eclipse.serializer.typing.KeyValue;
import org.eclipse.serializer.util.logging.Logging;
import org.eclipse.store.storage.exceptions.StorageException;
import org.slf4j.Logger;

/**
 * The StorageAdjacencyDataExporter is intended to export
 * all objects references to other objects in the storage.
 */
public interface StorageAdjacencyDataExporter
{
	/**
	 * Export adjacency data from the provided provided
	 * storage file. The Objects and references are represented by
	 * their storage ObjectID.
	 * 
	 * @param file the storage file to process.
	 */
	void exportAdjacencyData(StorageLiveDataFile file);
	
	/**
	 * Get the current AdjacencyFiles
	 * 
	 * @return a AdjacencyFiles object containing information on the exported data.
	 */
	AdjacencyFiles getExportetFiles();

	
	/**
	 * Defines the result of StorageAdjacencyDataExporter.
	 */
	public static interface AdjacencyFiles
	{
		Map<Long, Path> get();
		int getChannelIndex();
		
		public static class Default implements AdjacencyFiles
		{
			private final Map<Long, Path>  processedFiles;
			private final int channelIndex;
			
			public Default(final int channelIndex, final Map<Long, Path>  processedFiles)
			{
				this.channelIndex = channelIndex;
				this.processedFiles = processedFiles;
			}
			
			@Override
			public Map<Long, Path> get()
			{
				return this.processedFiles;
			}
			
			@Override
			public int getChannelIndex()
			{
				return this.channelIndex;
			}
		}
	}
	
	/**
	 * Default implementation of the StorageAdjacencyDataExporter interface
	 */
	public class Default implements StorageAdjacencyDataExporter
	{
		private final static Logger logger = Logging.getLogger(StorageAdjacencyDataExporter.class);
		
		private final Path exportDirectory;
		private final Hashtable<Long, BinaryReferenceTraverser[]> traverser = new Hashtable<>();
		private final XGettingTable<Long, PersistenceTypeDefinition> typeDefinitions;
		
		private final Map<Long, Path> processedFiles;
		private final int channelIndex;

		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////

		public Default(
			final StorageTypeDictionary typeDictionary,
			final Path exportDirectory,
			final int channelIndex
		)
		{
			super();
			this.channelIndex = channelIndex;
			this.processedFiles = new  HashMap<>();
			this.typeDefinitions = typeDictionary.allTypeDefinitions();
			this.exportDirectory = exportDirectory;
			this.createTraverser();
		}
				
		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////

		@Override
		public AdjacencyFiles getExportetFiles()
		{
			return new AdjacencyFiles.Default(this.channelIndex, this.processedFiles);
		}
		
		@Override
		public void exportAdjacencyData(final StorageLiveDataFile file)
		{
			HashMap<Long, long[]> referenceTable = new HashMap<>();
			
			ByteBuffer buffer = XMemory.allocateDirectNative((int)file.size());
			file.readBytes(buffer);
			
			long startAddress = XMemory.getDirectByteBufferAddress(buffer);
			long offset = 0;
			
			long address = 0;
			long size = 0;
			long typeID = 0;
			long objectID = 0;
			
			long keyCount = 0;
			long refCount = 0;
			
			while(offset < buffer.limit())
			{
				address = startAddress + offset;
				size = XMemory.get_long(address);
				typeID = XMemory.get_long(address + 8);
				objectID = XMemory.get_long(address + 16);
								
				if(Persistence.IdType.OID.isInRange(objectID))
				{
					long[] references = this.getReferenceIDs(address, typeID);
					referenceTable.put(objectID, references);
					
					keyCount++;
					refCount+=references.length;
				}
				
				offset += size;
			}
										
			XMemory.deallocateDirectByteBuffer(buffer);
						
			Path filePath = this.exportDirectory.resolve(file.file().name() + ".bin");
			logger.debug("Exporting reference meta data for file {} to {}", file.identifier(), filePath);
			
			this.serialize(referenceTable, filePath, keyCount, refCount);
			this.processedFiles.put(file.number(), filePath);
		}
		
		
		private void serialize(
			final HashMap<Long, long[]> referenceTable,
			final Path path,
			final long keyCount,
			final long refCount)
		{
			long estimatedbufferSize =
				((XMemory.byteSize_long() + XMemory.byteSize_int()) * keyCount) +
				(refCount * XMemory.byteSize_long());
												
			ByteBuffer buffer = ByteBuffer.allocate((int)estimatedbufferSize);
			
			for (Map.Entry<Long, long[]> entry : referenceTable.entrySet())
			{
				buffer.putLong(entry.getKey());
				buffer.putInt(entry.getValue().length);
				for (long l : entry.getValue())
				{
					try
					{
						buffer.putLong(l);
					}
					catch(Exception e)
					{
						throw new RuntimeException(e);
					}
				}
			}
			buffer.flip();
			
			
			try(FileChannel fc = FileChannel.open(path, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE, StandardOpenOption.CREATE))
			{
				fc.write(buffer);
			}
			catch(IOException e)
			{
				throw new RuntimeException(e);
			}
		}
			
		private void createTraverser()
		{
            for (KeyValue<Long, PersistenceTypeDefinition> e : this.typeDefinitions)
			{
                this.traverser.put(
					e.key(),
					BinaryReferenceTraverser.Static.deriveReferenceTraversers(
						e.value().allMembers(), false
				));
            }
        }
		
		private long[] getReferenceIDs(final long objectStartAddress, final long typeID)
		{
			BinaryReferenceTraverser[] tr = this.traverser.get(typeID);
			List<Long> referencesIDs = new ArrayList<>();
			
			if(tr == null)
			{
				throw new StorageException("No BinaryReferenceTraverser found for typeID " + typeID);
			}
			
			long a = Binary.toEntityContentOffset(objectStartAddress);

            for (BinaryReferenceTraverser binaryReferenceTraverser : tr)
			{
                a = binaryReferenceTraverser.apply(a, refId ->
                {
                    if (Swizzling.isProperId(refId))
                    {
                        referencesIDs.add(refId);
                    }
                });
            }
			
			return referencesIDs.stream().mapToLong(l -> l).toArray();
		}
	}
}
