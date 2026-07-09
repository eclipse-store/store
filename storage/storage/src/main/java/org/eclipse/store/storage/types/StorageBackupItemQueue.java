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

public interface StorageBackupItemQueue extends StorageBackupItemEnqueuer, StorageFileUser
{
	public boolean processNextItem(StorageBackupHandler handler, long timeoutMs) throws InterruptedException;
	
	public boolean isEmpty();
		
	public static StorageBackupItemQueue New()
	{
		return new StorageBackupItemQueue.Default();
	}
	
	public final class Default implements StorageBackupItemQueue
	{
		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////
		
		private final Item head = new Item(null);
		private       Item tail = this.head;
		
		
		
		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////
		
		Default()
		{
			super();
		}
		
		
		
		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////

		@Override
		public final boolean isEmpty()
		{
			return this.head.next == null;
		}
		
		@Override
		public final void enqueueCopyingItem(
			final StorageLiveChannelFile<?> sourceFile    ,
			final long               sourcePosition,
			final long               length
		)
		{
			this.internalEnqueueItem(new CopyItem(sourceFile, sourcePosition, length));
		}

		@Override
		public final void enqueueTruncatingItem(
			final StorageLiveChannelFile<?> file     ,
			final long               newLength
		)
		{
			// Signaling with a null sourceFile is a hack to avoid the complexity of multiple Item classes
			this.internalEnqueueItem(new TruncationItem(file, newLength));
		}
		
		@Override
		public void enqueueDeletionItem(
			final StorageLiveChannelFile<?> file
		)
		{
			// Signaling with a negative length is a hack to avoid the complexity of multiple Item classes
			this.internalEnqueueItem(new DeletionItem(file));
		}
		
		private void internalEnqueueItem(
			final Item item
		)
		{
			item.sourceFile.registerUsage(this);

			// no try-catch with unregisterUsage required since the following code is too simple to fail.
			synchronized(this.head)
			{
				this.tail = this.tail.next = item;
				this.head.notifyAll();
			}
		}

		@Override
		public void cancelPendingItemsFor(final StorageLiveChannelFile<?> file)
		{
			this.removeMatching(item -> item.sourceFile == file);
		}

		@Override
		public void trimPendingCopyItemsBeyond(final StorageLiveChannelFile<?> file, final long newLength)
		{
			this.removeMatching(item ->
				item.sourceFile == file
				&& item instanceof CopyItem
				&& ((CopyItem)item).sourcePosition >= newLength
			);
		}

		/**
		 * Unlinks {@code current} (whose predecessor in the chain is {@code previous}) and fixes
		 * {@code tail} if {@code current} was the last item. Callers must hold {@code this.head}'s
		 * monitor. Shared by {@link #processNextItem}'s single-item pop and {@link #removeMatching}'s
		 * arbitrary-position removal, so a future change to the tail-fixup invariant only has one
		 * place to get right.
		 */
		private void unlink(final Item previous, final Item current)
		{
			previous.next = current.next;
			if(current == this.tail)
			{
				this.tail = previous;
			}
		}

		/**
		 * Removes every still-queued item matching {@code predicate} and releases the usage each
		 * held, without processing them. Only reaches items still sitting in the queue; one
		 * already popped by the backup thread and mid-processing is outside this lock and not
		 * covered (an accepted, narrow residual race window).
		 * <p>
		 * The queue lock is a leaf (see {@link #processNextItem}): file-monitor calls
		 * (registerUsage/unregisterUsage) must never run while it is held, otherwise the queue
		 * lock and a {@code StorageLiveFile} monitor can be acquired in opposite orders by the
		 * housekeeping channel and the backup handler and deadlock. Removed items are therefore
		 * collected into a separate chain (reusing their own {@code next} field, now free) and
		 * their usage released only after the lock is released.
		 */
		private void removeMatching(final java.util.function.Predicate<? super Item> predicate)
		{
			Item removedHead = null;
			Item removedTail = null;

			synchronized(this.head)
			{
				Item previous = this.head;
				Item current  = this.head.next;
				while(current != null)
				{
					final Item next = current.next;
					if(predicate.test(current))
					{
						this.unlink(previous, current);
						current.next = null;
						if(removedHead == null)
						{
							removedHead = removedTail = current;
						}
						else
						{
							removedTail = removedTail.next = current;
						}
					}
					else
					{
						previous = current;
					}
					current = next;
				}
			}

			for(Item item = removedHead; item != null; item = item.next)
			{
				item.sourceFile.unregisterUsage(this);
			}
		}

		@Override
		public final boolean processNextItem(
			final StorageBackupHandler handler  ,
			final long                 timeoutMs
		)
			throws InterruptedException
		{
			final long timeBudgetBound = System.currentTimeMillis() + timeoutMs;
			final long waitInterval    = timeoutMs / 16;

			final Item itemToBeProcessed;

			// queue lock is a leaf: only structural mutation happens here. Processing and the
			// file-monitor work in unregisterUsageClosing must run outside it, otherwise the
			// queue lock and a StorageLiveFile monitor can be acquired in opposite orders by
			// the housekeeping channel and the backup handler, causing a deadlock.
			synchronized(this.head)
			{
				while(this.head.next == null)
				{
					if(!handler.isRunning())
					{
						return true;
					}

					if(System.currentTimeMillis() >= timeBudgetBound)
					{
						return false;
					}

					this.head.wait(waitInterval);
				}

				itemToBeProcessed = this.head.next;
				this.unlink(this.head, itemToBeProcessed);
			}

			try
			{
				itemToBeProcessed.processBy(handler);
			}
			finally
			{
				// the backup thread can be the last active part of an already shutdown storage, so it has to clean up.
				itemToBeProcessed.sourceFile.unregisterUsageClosing(this, null);
			}

			return true;
		}
		
		static class Item
		{
			///////////////////////////////////////////////////////////////////////////
			// instance fields //
			////////////////////
			
			final StorageLiveChannelFile<?> sourceFile    ;
			Item next;

			public Item(final StorageLiveChannelFile<?> sourceFile)
			{
				super();
				this.sourceFile = sourceFile;
			}
			
			///////////////////////////////////////////////////////////////////////////
			// methods //
			////////////
			
			public void processBy(final StorageBackupHandler handler)
			{
				//no-op
				return;
			}
			
		}
		
		static final class CopyItem extends Item
		{
			///////////////////////////////////////////////////////////////////////////
			// instance fields //
			////////////////////
			
			final long sourcePosition;
			final long length        ;
			
			
			///////////////////////////////////////////////////////////////////////////
			// constructors //
			/////////////////
			
			CopyItem(
				final StorageLiveChannelFile<?> sourceFile    ,
				final long                      sourcePosition,
				final long                      length
			)
			{
				super(sourceFile);
				this.sourcePosition = sourcePosition;
				this.length         = length        ;
			}
			
			@Override
			public void processBy(final StorageBackupHandler handler)
			{
				handler.copyFilePart(this.sourceFile, this.sourcePosition, this.length);
			}
		}
		
		static final class TruncationItem extends Item
		{
			///////////////////////////////////////////////////////////////////////////
			// instance fields //
			////////////////////
			
			final long length;
			
			
			///////////////////////////////////////////////////////////////////////////
			// constructors //
			/////////////////
			
			TruncationItem(
				final StorageLiveChannelFile<?> sourceFile,
				final long                      length
			)
			{
				super(sourceFile);
				this.length = length;
			}

			@Override
			public void processBy(final StorageBackupHandler handler)
			{
				handler.truncateFile(this.sourceFile, this.length);
			}
		}
		
		static final class DeletionItem extends Item
		{
			///////////////////////////////////////////////////////////////////////////
			// constructors //
			/////////////////
			
			DeletionItem(
				final StorageLiveChannelFile<?> sourceFile
			)
			{
				super(sourceFile);
			}

			@Override
			public void processBy(final StorageBackupHandler handler)
			{
				handler.deleteFile(this.sourceFile);
			}
			
		}
		
	}
	
}
