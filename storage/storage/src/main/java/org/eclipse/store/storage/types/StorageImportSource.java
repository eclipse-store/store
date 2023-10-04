package org.eclipse.store.storage.types;

/*-
 * #%L
 * EclipseStore Storage
 * %%
 * Copyright (C) 2023 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import java.util.function.Consumer;

public interface StorageImportSource
{
	public void iterateBatches(Consumer<? super StorageChannelImportBatch> iterator);
	
	public long copyTo(StorageFile target, long sourcePosition, long length);
	
	public boolean close();
	
	
	
	public static abstract class Abstract implements StorageImportSource
	{
		final StorageChannelImportBatch.Default headBatch;
		      StorageImportSource.Abstract      next     ;
		      
		protected Abstract(final StorageChannelImportBatch.Default headBatch)
		{
			this.headBatch = headBatch;
		}
		
		@Override
		public final void iterateBatches(final Consumer<? super StorageChannelImportBatch> iterator)
		{
			for(StorageChannelImportBatch.Default batch = this.headBatch; batch != null; batch = batch.batchNext)
			{
				iterator.accept(batch);
			}
		}
		
	}
	
}
