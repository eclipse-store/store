package org.eclipse.store.gigamap.types;

/*-
 * #%L
 * EclipseStore GigaMap
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

import org.eclipse.serializer.chars.VarString;
import org.eclipse.serializer.collections.BulkList;
import org.eclipse.serializer.collections.EqHashTable;
import org.eclipse.serializer.collections.types.XImmutableList;
import org.eclipse.serializer.collections.types.XImmutableTable;
import org.eclipse.serializer.math.XMath;
import org.eclipse.serializer.typing.KeyValue;
import org.eclipse.serializer.util.X;

import java.util.function.Consumer;

public final class DefaultStatistics
{
	///////////////////////////////////////////////////////////////////////////
	// static methods //
	///////////////////
	
	public static <E> IndicesStats<E> createStatistics(final BitmapIndices<E> indices)
	{
		int  totalDataMemorySize = 0;
		int  totalDataMemSizeDef = 0;
		long totalSegmentCount   = 0;
		int  totalChunkCount     = 0;
		
		final EqHashTable<String, BitmapIndex.Statistics<E>> indexStatses = EqHashTable.New();
		
		// this is the only concurrency-sensitive part. Everything else works on locally created instances.
		synchronized(indices.parentMap())
		{
			indices.iterate(index ->
			{
				final BitmapIndex.Statistics<E> indexStats = index.createStatistics();
				indexStatses.add(index.name(), indexStats);
			});
		}
		
		for(final BitmapIndex.Statistics<E> indexStats : indexStatses.values())
		{
			totalDataMemorySize += indexStats.totalDataMemorySize();
			
			if(indexStats instanceof IndexStats)
			{
				totalDataMemSizeDef += ((IndexStats<?>)indexStats).totalDataMemorySize;
				totalSegmentCount   += ((IndexStats<?>)indexStats).totalSegmentCount;
				totalChunkCount     += ((IndexStats<?>)indexStats).totalChunkCount;
			}
		}
		
		return new IndicesStats<>(
			indices,
			indexStatses.immure(),
			totalDataMemorySize,
			totalDataMemSizeDef,
			totalSegmentCount,
			totalChunkCount
		);
	}
	
	static <E> IndexStats<E> createStatistics(final BitmapIndex.TopLevel<E, ?> parent)
	{
		// identity equality is sufficient here since this is
		final BulkList<KeyValue<Object, EntryStats>> entries = BulkList.New();
		final Adder adder = new Adder(entries, parent.parentMap().size());
		
		// no locking needed here, since the whole method must be called under lock protection.
		parent.iterateEntries(adder);
		
		return new IndexStats<>(
			parent,
			entries.immure(),
			adder.totalDataMemorySize,
			adder.totalSegmentCount,
			adder.totalChunkCount
		);
	}
	
	static final class Adder implements Consumer<BitmapEntry<?, ?, ?>>
	{
		final BulkList<KeyValue<Object, EntryStats>> entries;
		final long entityCount; // always the same for all entries!
		int  totalDataMemorySize = 0;
		long totalSegmentCount   = 0;
		int  totalChunkCount     = 0;
				
		Adder(final BulkList<KeyValue<Object, EntryStats>> entries, final long entityCount)
		{
			super();
			this.entries     = entries    ;
			this.entityCount = entityCount;
		}
		
		@Override
		public final void accept(final BitmapEntry<?, ?, ?> entry)
		{
			final EntryStats entryStats = createStats(entry, this.entityCount);
			this.totalDataMemorySize += entryStats.totalDataMemorySize;
			this.totalSegmentCount   += entryStats.level1SegmentCount ;
			this.totalChunkCount     += entryStats.totalChunkCount    ;
			this.entries.add(X.KeyValue(entry.key(), entryStats));
		}
	}
	
	static EntryStats createStats(final BitmapEntry<?, ?, ?> entry, final long entityCount)
	{
		final BitmapLevel3 level3     = entry.getLevel3();
		final long level1SegmentCount = level3.totalSegmentCount();
		final int  level2SegmentCount = level3.localSegmentCount();
		
		int totalDataMemorySize = 0;
		int totalChunkCount     = 0;
		
		final BitmapLevel2[] segments = level3.segments;
		final Level2Stats[] level2Stats = new Level2Stats[segments.length];
		for(int i = 0; i < segments.length; i++)
		{
			final Level2Stats level2Stat = createStats(segments[i]);
			level2Stats[i] = level2Stat;
			
			// note: in consolidated state, the level2 memory size INCLUDES all level1 entries.
			totalDataMemorySize += level2Stat.totalDataMemorySize();
			totalChunkCount     += level2Stat.chunkCount();
		}
		
		return new EntryStats(
			entityCount,
			entry,
			level2Stats,
			level2SegmentCount,
			level1SegmentCount,
			totalDataMemorySize,
			totalChunkCount
		);
	}
	
	private static Level2Stats createStats(final BitmapLevel2 level2Segment)
	{
		if(level2Segment == null)
		{
			return new Level2Stats(new BitmapLevel2.BaseValueView(0, 0, 0), new Level1Stats[0]);
		}
		
		final BulkList<Level1Stats> level1Entries = BulkList.New(256);
		level2Segment.queryLevel1Entries(e -> level1Entries.add(new Level1Stats(e)));
		
		final BitmapLevel2.BaseValueView baseValues = level2Segment.queryBaseValues();
		
		// note: in consolidated state, the level2 memory size INCLUDES all level1 entries.
		return new Level2Stats(baseValues, level1Entries.toArray(Level1Stats.class));
	}
	
	
	
	public static final class IndicesStats<E> implements BitmapIndices.Statistics<E>
	{
		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////
		
		final BitmapIndices<E>                                   parent             ;
		final XImmutableTable<String, BitmapIndex.Statistics<E>> entries            ;
		final int                                                totalDataMemorySize;
		final int                                                totalDataMemSizeDef;
		final long                                               totalSegmentCount  ;
		final int                                                totalChunkCount    ;
		
		
		
		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////

		IndicesStats(
			final BitmapIndices<E>                                   parent             ,
			final XImmutableTable<String, BitmapIndex.Statistics<E>> entries            ,
			final int                                                totalDataMemorySize,
			final int                                                totalDataMemSizeDef,
			final long                                               totalSegmentCount  ,
			final int                                                totalChunkCount
		)
		{
			super();
			this.parent              = parent             ;
			this.entries             = entries            ;
			this.totalDataMemorySize = totalDataMemorySize;
			this.totalDataMemSizeDef = totalDataMemSizeDef;
			this.totalSegmentCount   = totalSegmentCount  ;
			this.totalChunkCount     = totalChunkCount    ;
		}
		
		
		
		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////

		@Override
		public XImmutableTable<String, BitmapIndex.Statistics<E>> entries()
		{
			return this.entries;
		}

		@Override
		public int totalDataMemorySize()
		{
			return this.totalDataMemorySize;
		}
		

		@Override
		public VarString assemble(final VarString vs, final int levels)
		{
			final long   entityCount       = this.parent.parentMap().size();
			final double avgBytesPerEntity = (double)this.totalDataMemorySize / entityCount;
			final double avgBytesPerChunk  = (double)this.totalDataMemSizeDef / this.totalChunkCount;
			final double avgBytesPerSegmnt = (double)this.totalDataMemSizeDef / this.totalSegmentCount;
			
			vs
			.add("BitmapIndices").lf()
			.add("Index Count       = ").add(this.entries.intSize()).lf()
			.add("Entity Count      = ").add(entityCount).lf()
			.add("Total Memory Size = ").add(this.totalDataMemorySize).lf()
			;
			if(this.totalDataMemorySize != this.totalDataMemSizeDef)
			{
				vs.add("TotalMemSize/Chunks= ").add(this.totalDataMemSizeDef).lf();
			}
			vs
			.add("Total Segmt Count = ").add(this.totalSegmentCount).lf()
			.add("Total Chunk Count = ").add(this.totalChunkCount).lf()
			.add("Avg. Bytes/Entity = ").add(XMath.round3(avgBytesPerEntity)).lf()
			.add("Avg. Bytes/Segmnt = ").add(XMath.round3(avgBytesPerSegmnt)).lf()
			.add("Avg. Bytes/Chunk  = ").add(XMath.round3(avgBytesPerChunk)).lf()
			.lf()
			;
			
			for(final BitmapIndex.Statistics<E> entry : this.entries.values())
			{
				entry.assemble(vs, levels);
			}
			
			return vs;
		}
		
		
	}
	
	public static final class IndexStats<E> implements BitmapIndex.Statistics<E>
	{
		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////
		
		final BitmapIndex<E, ?>                            parent ;
		final XImmutableList<KeyValue<Object, EntryStats>> entries;
			
		final int  totalDataMemorySize;
		final long totalSegmentCount  ;
		final int  totalChunkCount    ;
		
			
		
		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////
		
		IndexStats(
			final BitmapIndex<E, ?>                            parent             ,
			final XImmutableList<KeyValue<Object, EntryStats>> entries            ,
			final int                                          totalDataMemorySize,
			final long                                         totalSegmentCount  ,
			final int                                          totalChunkCount
		)
		{
			super();
			this.parent              = parent             ;
			this.entries             = entries            ;
			this.totalDataMemorySize = totalDataMemorySize;
			this.totalSegmentCount   = totalSegmentCount  ;
			this.totalChunkCount     = totalChunkCount    ;
		}
		
		
		
		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////
		
		@Override
		public BitmapIndex<E, ?> parent()
		{
			return this.parent;
		}
		
		@Override
		public XImmutableList<KeyValue<Object, EntryStats>> entries()
		{
			return this.entries;
		}
		
		@Override
		public int totalDataMemorySize()
		{
			return this.totalDataMemorySize;
		}
		
		@Override
		public VarString assemble(final VarString vs, final int levels)
		{
			final String entriesIndent     = "\t";
			final long   entityCount       = this.parent().parentMap().size();
			final double avgBytesPerEntity = entityCount == 0L ? 0.0 : (double)this.totalDataMemorySize / entityCount;
			final double avgBytesPerChunk  = (double)this.totalDataMemorySize / this.totalChunkCount;
			final double avgBytesPerSegmnt = (double)this.totalDataMemorySize / this.totalSegmentCount;
			
			vs
			.add("BitmapIndex \"").add(this.parent().name()).add('"').lf()
			.add("Entity Count      = ").add(entityCount).lf()
			.add("Total Memory Size = ").add(this.totalDataMemorySize).lf()
			.add("Total Segmt Count = ").add(this.totalSegmentCount).lf()
			.add("Total Chunk Count = ").add(this.totalChunkCount).lf()
			.add("Avg. Bytes/Entity = ").add(XMath.round3(avgBytesPerEntity)).lf()
			.add("Avg. Bytes/Segmnt = ").add(XMath.round3(avgBytesPerSegmnt)).lf()
			.add("Avg. Bytes/Chunk  = ").add(XMath.round3(avgBytesPerChunk)).lf()
			.add("Indexer           = ").add(this.parent().indexer().name()).lf()
			.add("Entries: (").add(this.entries.intSize()).add(")").lf()
			;

			if(levels > 0)
			{
				final int newLevels = levels - 1;
				for(final KeyValue<Object, ? extends EntryStats> entry : this.entries)
				{
					entry.value().assemble(vs, entriesIndent, newLevels);
				}
			}
			
			return vs.lf();
		}
		
		@Override
		public String toString()
		{
			return this.assemble(VarString.New()).toString();
		}
		
	}
	


	public static final class EntryStats implements BitmapIndex.KeyStatistics
	{
		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////

		final long                 entityCount        ;
		final BitmapEntry<?, ?, ?> entry              ;
		final Level2Stats[]        segments           ;
		final int                  level2SegmentCount ;
		final long                 level1SegmentCount ;
		final int                  totalDataMemorySize;
		final int                  totalChunkCount    ;
		
		
		
		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////
		
		EntryStats(
			final long                 entityCount        ,
			final BitmapEntry<?, ?, ?> entry              ,
			final Level2Stats[]        segments           ,
			final int                  level2SegmentCount ,
			final long                 level1SegmentCount ,
			final int                  totalDataMemorySize,
			final int                  totalChunkCount
		)
		{
			super();
			this.entityCount         = entityCount        ;
			this.entry               = entry              ;
			this.segments            = segments           ;
			this.level2SegmentCount  = level2SegmentCount ;
			this.level1SegmentCount  = level1SegmentCount ;
			this.totalDataMemorySize = totalDataMemorySize;
			this.totalChunkCount     = totalChunkCount    ;
		}
		
		
		
		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////

		@Override
		public int totalDataMemorySize()
		{
			return this.totalDataMemorySize;
		}
		
		VarString assemble(final VarString vs, final String indentation, final int levels)
		{
			final String level2Indent = indentation + "\t";
			
			final double avgBytesPerEntity = (double)this.totalDataMemorySize / this.entityCount;
						
			vs
			.add(indentation).add("Entry \"").add(this.entry.indexName()).add("\":\"").add(this.entry.key()).add("\":").lf()
			.add(indentation).add("En Total Memory Size = ").add(this.totalDataMemorySize).lf()
			.add(indentation).add("Avg.Bytes Per Entity = ").add(XMath.round3(avgBytesPerEntity)).lf()
			.add(indentation).add("level1SegmentCount   = ").add(this.level1SegmentCount).lf()
			.add(indentation).add("level2SegmentCount   = ").add(this.level2SegmentCount).lf()
			;
			
			if(levels > 0)
			{
				final int newLevels = levels - 1;
				final Level2Stats[] segments = this.segments;
				for(int i = 0; i < segments.length; i++)
				{
					final Level2Stats level2Stats = segments[i];
					vs.add(indentation).add("Level2Segment #").add(i).add(": ").lf();
					level2Stats.assemble(vs, level2Indent, newLevels);
				}
			}
			
			return vs;
		}
		
	}
	
	static final class Level2Stats
	{
		///////////////////////////////////////////////////////////////////////////
		// static methods //
		///////////////////
		
		private static int sumChunkCount(final Level1Stats[] segments)
		{
			int chunkCount = 0;
			for(final Level1Stats s : segments)
			{
				chunkCount += s.level1View.getChunkCount();
			}
			
			return chunkCount;
		}
		
		
		
		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////
		
		final BitmapLevel2.BaseValueView view      ;
		final Level1Stats[]              segments  ;
		final int                        chunkCount;
		
		
		
		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////
		
		Level2Stats(
			final BitmapLevel2.BaseValueView view    ,
			final Level1Stats[]              segments
		)
		{
			super();
			this.view       =               view     ;
			this.segments   =               segments ;
			this.chunkCount = sumChunkCount(segments);
		}
		
		
		
		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////
		
		final int type()
		{
			return this.view.type();
		}
		
		final int segmentCount()
		{
			return this.view.segmentCount();
		}
		
		final int chunkCount()
		{
			return this.chunkCount;
		}
		
		final int totalDataMemorySize()
		{
			return this.view.totalLength();
		}
		
		VarString assemble(final VarString vs, final String indentation, final int levels)
		{
			final String level1Indent = indentation + "\t";
			
			vs
			.add(indentation).add("L2 Total Memory Size = ").add(this.view.totalLength()).lf()
			.add(indentation).add("level1SegmentCount   = ").add(this.view.segmentCount()).lf()
			;
			
			if(levels > 0)
			{
				final int newLevels = levels - 1;
				final Level1Stats[] segments = this.segments;
				for(int i = 0; i < segments.length; i++)
				{
					final Level1Stats level1Stats = segments[i];
					vs.add(indentation).add("Level1Segment #").add(i).add(": ").lf();
					level1Stats.assemble(vs, level1Indent, newLevels);
				}
			}
			
			
			return vs;
		}

	}
	
	static final class Level1Stats
	{
		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////
		
		final BitmapLevel2.Level1EntryView level1View;
		
		
		
		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////
		
		Level1Stats(final BitmapLevel2.Level1EntryView level1View)
		{
			super();
			this.level1View = level1View;
		}
		
		
		
		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////
		
		VarString assemble(final VarString vs, final String indentation, final int levels)
		{
			vs
			.add(indentation).add("L1 Total Memory Size = ").add(this.level1View.getByteLength()).lf()
			.add(indentation).add("Chunks Count         = ").add(this.level1View.getChunkCount()).lf()
			;
			
			return vs;
		}
		
	}
	
}
