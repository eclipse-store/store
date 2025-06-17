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

import java.nio.file.Path;

class ComparableAdjacencyMap extends AdjacencyMap implements Comparable<ComparableAdjacencyMap>
{
	private long size = -1;
	private LongRange objectIdRange;
	private boolean updated;
		
	///////////////////////////////////////////////////////////////////////////
	// constructors //
	/////////////////

	public ComparableAdjacencyMap(final Path path)
	{
		super(null, path);
		this.load();
		this.unload();
	}

	///////////////////////////////////////////////////////////////////////////
	// methods //
	////////////

	public boolean updated()
	{
		return this.updated;
	}
	
	public boolean inRange(final long value)
	{
		if(this.objectIdRange != null)
		{
			return this.objectIdRange.inRange(value);
		}
		return false;
	}

	public boolean intersect(final ComparableAdjacencyMap other)
	{
		return this.objectIdRange.intersect(other.objectIdRange);
	}

	public void update()
	{
		this.size = this.map.size();
		if(!this.isEmpty())
		{
			this.objectIdRange = new ComparableAdjacencyMap.LongRange(this.map.firstKey(), this.map.lastKey());
		}
		this.updated = true;
	}

	public boolean isEmpty()
	{
		return this.size < 1;
	}

	@Override
	public String toString()
	{
		return "ComparableAdjacencyMap [path=" + this.path.getFileName() + ", size=" + this.size + ", objectIdRange=" + this.objectIdRange + "]";
	}
	
	public void load()
	{
		this.map = AdjacencyMap.deserializeReferenceMap(this.path);
		this.size = this.map.size();
		this.update();
	}
	
	public void unload()
	{
		this.map = null;
	}
	
	public void serialize()
	{
		AdjacencyMap.serialize(this.map, this.path);
		this.updated = false;
	}
	
	@Override
	public int compareTo(final ComparableAdjacencyMap other)
	{
		if(this.objectIdRange != null &&  other.objectIdRange != null)
		{
			return this.objectIdRange.compareTo(other.objectIdRange);
		}
		return 0;
	}
		
	
	private static class LongRange implements Comparable<LongRange>
	{
		private final long min;
		private final long max;
		
		public LongRange(final long min, final long max)
		{
			super();
			
			if(min > max) throw new IllegalArgumentException("Min value of range is greater then max value!");
			
			this.min = min;
			this.max = max;
		}
				
		@Override
		public String toString()
		{
			return "LongRange [min=" + this.min + ", max=" + this.max + "]";
		}
		
		public boolean intersect(final LongRange other)
		{
			return ((this.min <= other.max) && (this.max >= other.min));
		}
	
		@Override
		public int compareTo(final LongRange other)
		{
			if(this.min < other.min) return -1;
			if(this.min > other.min) return  1;
            return Long.compare(this.max, other.max);
        }
		
		public boolean inRange(final long value)
		{
			return (value >= this.min) && (value <= this.max);
		}
	}
}
