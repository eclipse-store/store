package org.eclipse.store.storage.types;

import java.nio.file.Files;
import java.nio.file.Path;

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

import java.util.Arrays;
import java.util.List;

import org.eclipse.store.storage.exceptions.StorageException;
import org.eclipse.store.storage.types.StorageAdjacencyDataExporter.AdjacencyFiles;

/**
 * Collect persisted object and reference data of all storage channels.
 * See {@link StorageAdjacencyDataExporter}.
 */
public interface  StorageRequestTaskExportAdjacencyData extends StorageRequestTask
{
	/**
	 * Return list of maps with missing object Ids
	 * and the reference chain to those.
	 * 
	 * @return a list of maps.
	 */
	public List<AdjacencyFiles> result();
	
	public final class Default
	extends StorageChannelSynchronizingTask.AbstractCompletingTask<Void>
	implements StorageRequestTaskExportAdjacencyData, StorageChannelTask
	{
		private final List<AdjacencyFiles> channelResults;
		private final Path exportDirectory;
		
		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////
		
		public Default(final long timestamp, final int channelCount, final StorageOperationController controller, final Path exportDirectory)
		{
			super(timestamp, channelCount, controller);
			this.channelResults = Arrays.asList(null, null, null, null);
			this.exportDirectory = this.ensureDirectory(exportDirectory);
		}
		
		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////
	
		@Override
		protected final Void internalProcessBy(final StorageChannel channel)
		{
			final AdjacencyFiles result = channel.collectAdjacencyData(this.exportDirectory);
			this.channelResults.set(channel.channelIndex(), result);
			
			return null;
		}
		
		@Override
		protected void postCompletionSuccess(final StorageChannel channel, final Void result) throws InterruptedException
		{
			super.postCompletionSuccess(channel, result);
		}
		
		@Override
		public final List<AdjacencyFiles> result()
		{
			return this.channelResults;
		}
		
		private Path ensureDirectory(final Path exportDirectory)
		{
			if(!Files.exists(exportDirectory))
			{
				throw new StorageException("Directory not found: " + exportDirectory);
			}
			
			return exportDirectory;
		}
	}
}
