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
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.serializer.persistence.types.Persistence;

final class AdjacencySet
{
	private ByteBuffer buffer;
	private Set<Long> references;
	private Path path;
	private FileChannel fc;
	private boolean isEmpty;
	
	///////////////////////////////////////////////////////////////////////////
	// constructors //
	/////////////////
	
	AdjacencySet(final AdjacencyMap map)
	{
		super();
		this.init(map);
	}
	
	AdjacencySet(final Path path)
	{
		super();
		this.path = path;
	}
	
	///////////////////////////////////////////////////////////////////////////
	// methods //
	////////////
	
	public Path getPath()
	{
		return this.path;
	}
	
	public Set<Long> getReferences()
	{
		return this.references;
	}
			
	private void init(final AdjacencyMap referenceMap)
	{
		Map<Long, long[]> map = referenceMap.getMap();
		
		this.path = AdjacencyDataConverter.Default.deriverPath(referenceMap.getPath(), ".ref");
		
		this.references = new HashSet<>();
						
		map.forEach((k,v) -> {
			for(long r : v)
			{
				if(Persistence.IdType.OID.isInRange(r))
				{
					//referenced id not in this map
					if(!map.containsKey(r))
					{
						this.getReferences().add(r);
					}
				}
			}
		});
	}
	
	public void load(final boolean truncate)
	{
		try
		{
			this.fc = FileChannel.open(this.getPath(),
					StandardOpenOption.READ,
					StandardOpenOption.WRITE);
			
			this.buffer = ByteBuffer.allocate((int) this.fc.size());
			this.fc.read(this.buffer);
			if(truncate)
			{
				this.fc.truncate(0);
			}
			this.buffer.flip();
			
			this.references = new HashSet<>(this.buffer.capacity() / Long.BYTES);
			
			while(this.buffer.position() < this.buffer.limit())
			{
				this.getReferences().add(this.buffer.getLong());
			}
			this.buffer.clear();
		}
		catch(ClosedByInterruptException e)
		{
			//suppress ClosedByInterruptException
		}
		catch(IOException e)
		{
			try
			{
				this.fc.close();
			}
			catch(IOException closeException)
			{
				//suppress failed file channel close exception
			}
			throw new RuntimeException(e);
		}
	}
	
	public void reduce(final AdjacencyMap map)
	{
		this.getReferences().removeAll(map.getMap().keySet());
							
		if(this.getReferences().isEmpty())
		{
			this.isEmpty = true;
		}
	}
	
	public void store()
	{
		if(this.getReferences() == null)
		{
			return;
		}
						
		if(this.buffer == null)
		{
			int size = this.getReferences().size() * Long.BYTES;
			this.buffer = ByteBuffer.allocate(size);
		}
						
		for(long r : this.getReferences())
		{
			this.buffer.putLong(r);
		}
		this.buffer.flip();
		
		try
		{
			if(this.fc == null)
			{
				this.fc = FileChannel.open(this.getPath(),
					StandardOpenOption.CREATE,
					StandardOpenOption.READ,
					StandardOpenOption.WRITE);
			}
			
			if(this.fc.write(this.buffer) < this.buffer.limit())
			{
				throw new RuntimeException("Failed writing to " + this.path + ", not all data written!");
			}
			
		}
		catch(IOException e)
		{
			try
			{
				this.fc.close();
			}
			catch(IOException closeException)
			{
				//suppress failed file channel close exception
			}
			
			throw new RuntimeException(e);
		}
	}
	
	public void unload()
	{
		this.references = null;
		this.buffer = null;
	}
				
	public boolean isEmpty()
	{
		return this.isEmpty;
	}
	
	public void release()
	{
		 this.unload();
		 if(this.fc != null)
		 {
			 if(this.fc.isOpen())
			 {
				try
				{
					this.fc.close();
				}
				catch(IOException e)
				{
					//suppress failed file channel close exception
				}
			 }
		 }
	}

}
