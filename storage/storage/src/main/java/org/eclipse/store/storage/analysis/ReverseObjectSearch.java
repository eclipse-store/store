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
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.eclipse.serializer.util.logging.Logging;
import org.eclipse.store.storage.types.StorageAdjacencyDataExporter.AdjacencyFiles;
import org.slf4j.Logger;

public interface ReverseObjectSearch
{
	/**
	 * Search for missing objects by id.
	 * 
	 * @param objectIDs a set of id to search
	 * @return ObjectParents instance providing the search results.
	 */
	public ObjectParents searchObjectIDs(final Set<Long> objectIDs);
	
	/**
	 * Create a new default instance of the ReverseObjectSearch.
	 * 
	 * @param adjacencyFiles to be processed.
	 * @return a new ReverseObjectSearch instance.
	 */
	public static ReverseObjectSearch New(final List<AdjacencyFiles> adjacencyFiles)
	{
		return new Default(adjacencyFiles);
	}
	
	public class Default implements ReverseObjectSearch
	{
		private final static Logger logger = Logging.getLogger(ReverseObjectSearch.class);
		
		private final List<AdjacencyFiles> adjacencyFiles;
		private final LinkedList<ComparableAdjacencyMap> reverseAdjacencyMaps;
		
		
		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////
	
		/**
		 * Create a new instance of the ReverseObjectSearch.
		 * 
		 * @param adjacencyFiles to be processed.
         */
		public Default(final List<AdjacencyFiles> adjacencyFiles)
		{
			super();
			this.adjacencyFiles = adjacencyFiles;
			this.reverseAdjacencyMaps = this.buildReverseAdjacencyMaps();
		}
	
		///////////////////////////////////////////////////////////////////////////
		// methods //
		///////////
			
		@Override
		public ObjectParents searchObjectIDs(final Set<Long> objectIDs)
		{
			while(this.hasOverlapping(this.reverseAdjacencyMaps))
			{
				this.sort(this.reverseAdjacencyMaps);
				Collections.sort(this.reverseAdjacencyMaps);
			}
			
			return this.search(objectIDs);
		}
		
		private boolean hasOverlapping(final LinkedList<ComparableAdjacencyMap> list)
		{
			for(final ComparableAdjacencyMap mapA : list)
			{
				for(final ComparableAdjacencyMap mapB : list)
				{
					if(mapA.isEmpty() || mapB.isEmpty()) {
						continue;
					}
					
					final boolean overlapping = mapA.intersect(mapB);
					logger.trace("overlap test {} > {} : {}", mapA.path.getFileName(), mapB.path.getFileName(), overlapping);
					
					if(mapA != mapB)
					{
						if(overlapping)
							return true;
					}
				}
			}
			return false;
		}
		
		private ObjectParents.Default search(final Set<Long> objectIDs)
		{
			final Map<Long, long[]> foundParents = new TreeMap<>();

            final TreeSet<Long> next = new TreeSet<>(objectIDs);
			
			while(!next.isEmpty())
			{
				Long currentID = next.pollFirst();
						
				logger.debug("searching processing id: {} ", currentID);
			
				final ComparableAdjacencyMap map = this.getMapFor(currentID);
				if(map != null)
				{
					if(map.map == null)
					{
						map.load();
					}
					
					while(currentID != null)
					{
						final long[] parents = map.map.get(currentID);
						if(parents == null)
						{
							logger.trace("no parents for id {} found in map {} !", currentID, map);
						}
						else
						{
							foundParents.put(currentID, parents);
									
							for(final long parentID : parents)
							{
								if(foundParents.get(parentID) == null)
								{
									//if parent object id not found search for it in next cycle.
									next.add(parentID);
								}
							}
						}
												
						if(!next.isEmpty())
						{
							currentID = next.first();
						}
												
						if(!next.removeIf(map::inRange))
						{
							currentID = null;
						}
					}
					
					map.unload();
					
				}
				else
				{
					logger.trace("ID {} not found in map ranges!", currentID);
				}
			}
					
			return new ObjectParents.Default(foundParents);
		}
			
		private ComparableAdjacencyMap getMapFor(final long id)
		{
			for(final ComparableAdjacencyMap map : this.reverseAdjacencyMaps)
			{
				if(map.inRange(id))
					return map;
			}
			return null;
		}
		
		private void sort(final LinkedList<ComparableAdjacencyMap> list)
		{
			long counter = 0;
			
			for(final ComparableAdjacencyMap mapA : list)
			{
				if(mapA.isEmpty()) {
					continue;
				}
				mapA.load();
				
				for(final ComparableAdjacencyMap mapB : list)
				{
					if(mapA == mapB) {
						continue;
					}
																
					if(mapB.isEmpty()) {
						continue;
					}
					if(mapA.intersect(mapB))
					{
						mapB.load();
						
						this.mergeMaps(mapA, mapB);
						
						mapA.update();
						mapB.update();
						
						if(mapB.updated())
						{
							mapB.serialize();
							logger.trace("serialized {} ", mapB.path.getFileName());
						}
						mapB.unload();
					}
					
				}
				if(mapA.updated())
				{
					mapA.serialize();
				}
				mapA.unload();
				
				logger.debug("Progress: {} of {}", ++counter, list.size());
			}
		}
		
		private void mergeMaps(final ComparableAdjacencyMap mapA, final ComparableAdjacencyMap mapB)
		{
			logger.trace("merging {} with {}", mapA, mapB);
			
			final TreeMap<Long, long[]> map1 = mapA.map;
			final TreeMap<Long, long[]> map2 = mapB.map;
	
			map1.putAll(map2);
			map2.clear();
	
			for(int i = 0; i < map1.size() / 2; i++)
			{
				final var e = map1.lastEntry();
				map2.put(e.getKey(), e.getValue());
				map1.remove(e.getKey());
			}
	
			mapA.update();
			mapB.update();
		}
			
			
		private LinkedList<ComparableAdjacencyMap> buildReverseAdjacencyMaps()
		{
			final LinkedList<ComparableAdjacencyMap> backReferenceMaps = new LinkedList<>();
	
			for(final AdjacencyFiles channel : this.adjacencyFiles)
			{
				for(final Entry<Long, Path> entry : channel.get().entrySet())
				{
					final Path path = AdjacencyDataConverter.Default.derivePath(entry.getValue(), ".brf");
					
					backReferenceMaps.add(new ComparableAdjacencyMap(path));
				}
			}
			
			return backReferenceMaps;
		}
	}
}
