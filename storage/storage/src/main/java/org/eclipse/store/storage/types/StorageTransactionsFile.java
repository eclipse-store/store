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

import org.eclipse.serializer.afs.types.AFile;

public interface StorageTransactionsFile extends StorageChannelFile, StorageBackupableFile
{
	@Override
	public default StorageBackupTransactionsFile ensureBackupFile(final StorageBackupInventory creator)
	{
		return creator.ensureTransactionsFile(this);
	}
	
	
	
	@FunctionalInterface
	public interface Creator<F extends StorageTransactionsFile>
	{
		public F createTransactionsFile(AFile file, int channelIndex);
	}
}

