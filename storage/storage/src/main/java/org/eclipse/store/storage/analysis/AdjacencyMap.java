package org.eclipse.store.storage.analysis;

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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

class AdjacencyMap
{
	protected TreeMap<Long, long[]> map;
	final protected Path path;
	
	///////////////////////////////////////////////////////////////////////////
	// constructors //
	/////////////////
	
	public AdjacencyMap(final TreeMap<Long, long[]> refMap, final Path path)
	{
		this.map = refMap;
		this.path = path;
	}

	///////////////////////////////////////////////////////////////////////////
	// methods //
	////////////
		
	public TreeMap<Long, long[]> getMap()
	{
		return this.map;
	}
	
	public Path getPath()
	{
		return this.path;
	}

	/**
	 * Helper method to deserialize a TreeMap&ltLong, long[]&gt&gt from a file.
	 * 
	 * @param path the input file
	 * @return the loaded TreeMap
	 */
	public static TreeMap<Long, long[]> deserializeReferenceMap(final Path path)
	{
		final TreeMap<Long, long[]> result = new TreeMap<>();
		
		try(FileChannel fc = FileChannel.open(path, StandardOpenOption.READ))
		{
			final ByteBuffer buffer = ByteBuffer.allocate((int) fc.size());
			fc.read(buffer);
			buffer.flip();
			
			while(buffer.position() < buffer.limit())
			{
				final long k = buffer.getLong();
				final int len = buffer.getInt();
				final long[] v = new long[len];
				for (int i = 0; i < len; i++)
				{
					v[i] = buffer.getLong();
				}
				result.put(k, v);
			}
			
		}
		catch(final IOException e)
		{
			throw new RuntimeException(e);
		}
		
		return result;
	}
	
	/**
	 * Helper method to serialize a Map&ltLong, long[]&gt&gt to a file.
	 * 
	 * @param map the map to serialize
	 * @param path the target file path
	 */
	public static void serialize(final Map<Long, long[]> map, final Path path)
	{
		final long estimatedBufferSize = 1024*1024*4;
					
		final List<ByteBuffer> buffers = new LinkedList<>();
		
		ByteBuffer buffer = ByteBuffer.allocate((int)estimatedBufferSize);
		buffers.add(buffer);
			
		for (final Map.Entry<Long, long[]> entry : map.entrySet())
		{
			final long[] value = entry.getValue();
			final long requiredSize = Long.BYTES + Integer.BYTES + (value.length * Long.BYTES);
			
			if(buffer.remaining() < requiredSize)
			{
				buffer.flip();
				buffer = ByteBuffer.allocate((int)requiredSize);
				buffers.add(buffer);
			}
				
			buffer.putLong(entry.getKey());
			buffer.putInt(value.length);
			for (final long l : value)
			{
	            buffer.putLong(l);
	        }
		}
		buffer.flip();
				
		try(FileChannel fc = FileChannel.open(path, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE, StandardOpenOption.CREATE))
		{
			for(final ByteBuffer byteBuffer : buffers)
			{
				if(fc.write(byteBuffer) < byteBuffer.limit() )
				{
					throw new RuntimeException("Failed writing to " + path + ", not all data written!");
				}
			}
		}
		catch(final IOException e)
		{
			throw new RuntimeException(e);
		}
				
	}
	
	/**
	 * Helper method to serialize a Map&ltLong, Set&ltLong&gt&gt to a file.
	 * 
	 * @param map the hashmap to serialize
	 * @param path the target file path
	 * @param keyCount the number of all keys
	 * @param valueCount the number of all elements in all value sets
	 */
	public static void serialize(final Map<Long,  Set<Long>> map, final Path path, final long keyCount, final long valueCount)
	{
		final long estimateBufferSize = ((Long.BYTES + Integer.BYTES) * keyCount)
			+ (valueCount * Long.BYTES);
					
		final ByteBuffer buffer = ByteBuffer.allocate((int)estimateBufferSize);
		
		for (final Entry<Long, Set<Long>> entry : map.entrySet())
		{
			buffer.putLong(entry.getKey());
			buffer.putInt(entry.getValue().size());
			for (final long l : entry.getValue())
			{
                buffer.putLong(l);
            }
		}
		buffer.flip();
		
		
		try(FileChannel fc = FileChannel.open(path, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE, StandardOpenOption.CREATE))
		{
			if(fc.write(buffer) < buffer.limit() )
			{
				throw new RuntimeException("Failed writing to " + path + ", not all data written!");
			}
		}
		catch(final IOException e)
		{
			throw new RuntimeException(e);
		}
	}
}
