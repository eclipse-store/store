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

import java.util.Iterator;
import java.util.NoSuchElementException;

import static org.eclipse.serializer.util.X.notNull;


/**
 * GigaIterator provides an interface for iterating over elements in a {@link GigaMap}
 * and supports safe and efficient iteration with features for resource management
 * and state control.
 *
 * @param <E> the type of elements returned by this iterator
 */
public interface GigaIterator<E> extends Iterator<E>, AutoCloseable
{
	/**
	 * Closes the iterator and releases any resources associated with it.
	 * After calling this method, the iterator is considered closed and cannot be used further.
	 * This method is intended to ensure proper cleanup of resources.
	 * <p>
	 * Implementations should handle multiple invocations of this method gracefully.
	 * If the iterator is already closed, further invocations of this method
	 * should not throw exceptions or perform additional cleanup actions.
	 */
	@Override
	public void close();
	
	/**
	 * Checks if the iterator has been closed.
	 *
	 * @return true if the iterator is closed, false otherwise.
	 */
	public boolean isClosed();
	
	/**
	 * Retrieves the parent GigaMap instance associated with this iterator.
	 * The parent represents the collection or source from which this iterator
	 * is iterating over.
	 *
	 * @return the parent GigaMap associated with this iterator, or null if no parent is set.
	 */
	public GigaMap<?> parent();
	
	/**
	 * Processes the next entry in the iteration by passing it to the provided {@link EntryConsumer}.
	 * The consumer is invoked with the entity ID and the corresponding element.
	 *
	 * @param consumer the consumer instance that processes the entity ID and element.
	 *                 Must implement {@link EntryConsumer}, specifying how the entity ID and element
	 *                 are handled. Cannot be null.
	 * @param <I>      a type parameter representing an implementation of
	 *                 {@link EntryConsumer} that is capable of handling the entries.
	 * @throws NoSuchElementException if the iteration has no more elements
	 */
	public <I extends EntryConsumer<? super E>> void nextIndexed(final I consumer);
	
	
	
	public final class Default<E> extends AbstractGigaIterating<E> implements GigaIterator<E>, GigaMap.Reading
	{
		final static class Entry<E>
		{
			final E    entity;
			final long id;
			
			Entry(final E entity, final long id)
			{
				super();
				this.entity = entity;
				this.id     = id;
			}
		}
		
		
		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////
		
		// parent must be referenced separately because resolver might not use/reference it at all.
		private final GigaMap.Default<E> parent;
		
		private boolean isActive = true;
		private int currentBitPosition = -1; // must be -1 due to pre-increment logic
		
		private Entry<E> next = null;
		
		
		
		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////

		Default(
			final GigaMap.Default<E>       parent  ,
			final BitmapResult.Resolver<E> resolver,
			final long                     idStart ,
			final long                     idBound ,
			final BitmapResult[]           results
		)
		{
			super(resolver, idStart, idBound, results);
			this.parent = parent;
		}
		
		
		
		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////
		
		@Override
		public final GigaMap.Default<E> parent()
		{
			return this.parent;
		}
		
		@Override
		public final boolean hasNext()
		{
			synchronized(this.parent())
			{
				// must close in any and all cases where next is null. Including no elements and any throwable.
				try
				{
					if(this.next != null || (this.next = this.scrollToNextNonNullElement()) != null)
					{
						return true;
					}
					
					this.close();
					return false;
				}
				catch(final Throwable t)
				{
					this.close();
					throw t;
				}
			}
		}
		
		@Override
		public final E next()
		{
			return this.nextEntry().entity;
		}
		
		@Override
		public <I extends EntryConsumer<? super E>> void nextIndexed(final I consumer)
		{
			final Entry<E> entry = this.nextEntry();
			consumer.accept(entry.id, entry.entity);
		}
		
		private Entry<E> nextEntry()
		{
			synchronized(this.parent())
			{
				final Entry<E> next;
				if(this.next != null)
				{
					// #hasNext already had the next element prepared, so just consume it.
					next = this.next;
					this.next = null;
				}
				else if((next = this.scrollToNextNonNullElement()) == null)
				{
					// no element and no more data to get the next one, hence exception
					throw new NoSuchElementException();
				}
				return next;
			}
		}
		
		private Entry<E> scrollToNextNonNullElement()
		{
			// Copied from GigaIteration#execute and adjusted for Iterator principle (iteration devided betweeen two methods)
			while(this.isActive)
			{
				long bitmapValue = this.currentBitmapValue;
				long valueBaseId = this.bitValBaseId;
				for(int bitPosition = this.currentBitPosition;;)
				{
					do
					{
						if(++bitPosition >= Long.SIZE)
						{
							if(!this.scrollToNextBitmapValue())
							{
								// scrolling logic reached end of data and did already setup the internal state to abort.
								return null;
							}
							bitmapValue = this.currentBitmapValue;
							valueBaseId = this.bitValBaseId;
							bitPosition = 0;
						}
					}
					while((bitmapValue & 1L<<bitPosition) == 0L);
					this.currentBitPosition = bitPosition;
					
					// Null check filters out null entries when using a top-level not condition. No performance impact.
					final long entityId = valueBaseId + bitPosition;
					final E    entity   = this.resolver.get(entityId);
					if(entity == null)
					{
						continue;
					}
					
					// keep the next non-null entity ready for #next.
					return this.next = new Entry<>(entity, entityId);
				}
			}
			
			// iterator has been closed/deactivated. No more elements.
			return null;
		}
			
		@Override
		public void close()
		{
			this.parent.closeIterator(this);
		}
		
		@Override
		public final boolean isClosed()
		{
			return !this.isActive;
		}
		
		@Override
		public final void setInactive()
		{
			// these values make #hasNext and #next dysfunctional forever.
			this.currentBitmapValue = -1L; // these two will cause #hasNext to skip state progressing logic
			this.currentBitPosition =   0; // these two will cause #hasNext to skip state progressing logic
			this.bitValBaseId       = Long.MIN_VALUE; // will always cause an exception when calling #next
			this.isActive           = false; // makes #hasNext return false
		}
		
		public void clearIterationState()
		{
			if(!this.isActive)
			{
				// iteration state has already been cleared and will not be set up again.
				return;
			}
			
			this.clearResultsIterationState();
		}
		
	}
	
	public final class Wrapping<E> implements GigaIterator<E>, GigaMap.Reading
	{
		final static class Entry<E>
		{
			final E    entity;
			final long id;
			
			Entry(final E entity, final long id)
			{
				super();
				this.entity = entity;
				this.id     = id;
			}
		}
		
		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////
		
		// parent must be referenced separately because resolver might not use/reference it at all.
		private final GigaMap.Default<E>       parent    ;
		private final ResultIdIterator         idIterator;
		private final BitmapResult.Resolver<E> resolver  ;

		private Entry<E> next = null;
		
		
		
		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////

		Wrapping(
			final GigaMap.Default<E>       parent    ,
			final ResultIdIterator         idIterator,
			final BitmapResult.Resolver<E> resolver
		)
		{
			super();
			this.parent     = parent    ;
			this.idIterator = idIterator;
			this.resolver   = resolver  ;
		}
		
		
		
		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////

		@Override
		public GigaMap<?> parent()
		{
			return this.idIterator.parent();
		}

		@Override
		public final boolean hasNext()
		{
			if(this.next != null || (this.next = this.scrollToNextNonNullElement()) != null)
			{
				return true;
			}

			// must close at the end to unregister from the parent GigaMap.
			this.close();

			return false;
		}

		@Override
		public final E next()
		{
			return this.nextEntry().entity;
		}
		
		@Override
		public <I extends EntryConsumer<? super E>> void nextIndexed(final I consumer)
		{
			final Entry<E> entry = this.nextEntry();
			consumer.accept(entry.id, entry.entity);
		}
		
		private Entry<E> nextEntry()
		{
			final Entry<E> next;
			if(this.next != null)
			{
				// #hasNext already had the next element prepared, so just consume it.
				next = this.next;
				this.next = null;
			}
			else if((next = this.scrollToNextNonNullElement()) == null)
			{
				// no element and no more data to get the next one, hence exception
				throw new NoSuchElementException();
			}
			return next;
		}
		
		private Entry<E> scrollToNextNonNullElement()
		{
			while(this.idIterator.hasNextId())
			{
				// then must ensure a non-null entity for the marked entityId (baseId plus bit position)
				final long nextId = this.idIterator.nextId();
				final E    next   = this.resolver.get(nextId);
				if(next == null)
				{
					// surplus 0 bits in the last long value and Query NOT logic can produce null lookups. So try again.
					continue;
				}
				
				// found the next non-null entity.
				return this.next = new Entry<>(next, nextId);
			}
			return null;
		}

		@Override
		public void close()
		{
			this.parent.closeIterator(this);
			this.idIterator.close();
		}

		@Override
		public boolean isClosed()
		{
			return this.idIterator.isClosed();
		}

		@Override
		public void setInactive()
		{
			this.idIterator.setInactive();
		}
		
	}
	
	
	public static <E> Empty<E> Empty(final GigaMap.Default<E> parent)
	{
		return new Empty<>(
			notNull(parent)
		);
	}
	
	public final class Empty<E> implements GigaIterator<E>, GigaMap.Reading
	{
		// parent must be referenced separately because resolver might not use/reference it at all.
		private final GigaMap.Default<E> parent;
		
		
		
		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////
		
		Empty(final GigaMap.Default<E> parent)
		{
			super();
			this.parent = parent;
		}
		
		
		
		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////
		
		@Override
		public boolean hasNext()
		{
			return false;
		}
		
		@Override
		public E next()
		{
			throw new NoSuchElementException();
		}
		
		@Override
		public <I extends EntryConsumer<? super E>> void nextIndexed(final I consumer)
		{
			throw new NoSuchElementException();
		}
		
		@Override
		public void setInactive()
		{
			// no-op, empty iterator is already inactive
		}
		
		@Override
		public void close()
		{
			// no-op, empty iterator is already closed.
		}
		
		@Override
		public boolean isClosed()
		{
			return true;
		}
		
		@Override
		public GigaMap<?> parent()
		{
			return this.parent;
		}
		
	}

}
