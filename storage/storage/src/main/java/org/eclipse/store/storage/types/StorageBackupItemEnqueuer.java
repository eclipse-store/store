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

public interface StorageBackupItemEnqueuer
{
	public void enqueueCopyingItem(
		StorageLiveChannelFile<?> sourceFile    ,
		long                      sourcePosition,
		long                      length
	);
	
	public void enqueueTruncatingItem(
		StorageLiveChannelFile<?> file     ,
		long                      newLength
	);
	
	public void enqueueDeletionItem(
		StorageLiveChannelFile<?> file
	);

	/**
	 * Removes every still-queued item referencing {@code file} and releases the usage each held,
	 * without processing them. Used before a physical delete: once the file is gone, the backup
	 * thread must not try to copy/truncate/delete a source that no longer exists.
	 *
	 * @param file the file whose pending items are no longer valid.
	 */
	public void cancelPendingItemsFor(
		StorageLiveChannelFile<?> file
	);

	/**
	 * Removes still-queued copy items for {@code file} whose source range starts at or beyond
	 * {@code newLength} and releases the usage each held. Used before a physical truncate: a copy
	 * item covering bytes the truncate is about to remove would otherwise be processed against a
	 * source that no longer has them.
	 *
	 * @param file      the file being truncated.
	 * @param newLength the file's new (shorter) length; copy items at or beyond it are invalid.
	 */
	public void trimPendingCopyItemsBeyond(
		StorageLiveChannelFile<?> file     ,
		long                      newLength
	);

}
