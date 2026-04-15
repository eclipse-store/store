package org.eclipse.store.gigamap.types;

import java.util.NoSuchElementException;

/**
 * A {@link GigaIterator} that lazily yields entities matched by a bitmap-based query.
 * <p>
 * The iterator drives an {@link AbstractBitmapIterating} traversal over one or more
 * {@link BitmapResult}s, resolving each matched entity id via an {@link EntityResolver}.
 * Null entities (which can arise from NOT-condition arithmetic over removed entries) are
 * transparently skipped so that every call to {@link #next()} yields a real entity.
 * <p>
 * A {@code BitmapIterator} holds a read lock on its parent {@link GigaMap} for the duration
 * of the iteration. Callers must always {@link #close()} the iterator — preferably via
 * try-with-resources — to release the lock. The iterator auto-closes itself once the last
 * element has been returned or when an exception is thrown during iteration.
 *
 * @param <E> the entity type of the parent {@link GigaMap}
 *
 * @see GigaIterator
 * @see EntityResolver
 */
public final class BitmapIterator<E> extends AbstractBitmapIterating<E> implements GigaIterator<E>, GigaMap.Reading
{
	///////////////////////////////////////////////////////////////////////////
	// instance fields //
	////////////////////

	// parent must be referenced separately because resolver might not use/reference it at all.
	private final GigaMap.Default<E> parent  ;
	private final EntityResolver<E>  resolver;

	private boolean isActive = true;

	private E    next   = null;
	private long nextId = -1L;



	///////////////////////////////////////////////////////////////////////////
	// constructors //
	/////////////////

	BitmapIterator(
		final GigaMap.Default<E> parent   ,
		final EntityIdMatcher    idMatcher,
		final EntityResolver<E>  resolver ,
		final long               idStart  ,
		final long               idBound  ,
		final BitmapResult[]     results
	)
	{
		// currentBitPosition must be -1 due to pre-increment logic
		super(idMatcher, idStart, idBound, results, -1);
		this.parent   = parent  ;
		this.resolver = resolver;
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
		synchronized(this.parent())
		{
			final E next;
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

	@Override
	public <I extends EntryConsumer<? super E>> void nextIndexed(final I consumer)
	{
		final E entity = this.next();
		consumer.accept(this.nextId, entity);
	}

	@Override
	protected final boolean handleEntityId(final long entityId)
	{
		// Null check filters out null entries when using a top-level not condition. No performance impact.
		final E entity = this.resolver.get(entityId);
		if(entity == null)
		{
			return false;
		}

		// keep the next non-null entity ready for #next.
		this.next   = entity  ;
		this.nextId = entityId;

		return true;
	}

	private E scrollToNextNonNullElement()
	{
		if(this.isActive)
		{
			// scrolls to the next element because #handleEntityId returns true on the first hit, meaning abort loop.
			this.execute();

			// current element or null if there are no more elements.
			return this.next;
		}

		// iterator has been closed/deactivated. No more elements.
		return null;
	}

	@Override
	protected final void terminateLoop()
	{
		this.next = null;
	}

	@Override
	public void close()
	{
		this.parent.closeReader(this);
	}

	@Override
	public final boolean isClosed()
	{
		return !this.isActive;
	}

	@Override
	public final void setInactive()
	{
		// these values make #hasNext and #next disfunctional forever.
		this.currentBitmapValue = -1L;            // these two will cause #hasNext to skip state progressing logic
		this.setCurrentBitPosition(0);            // these two will cause #hasNext to skip state progressing logic
		this.bitValBaseId       = Long.MIN_VALUE; // will always cause an exception when calling #next
		this.isActive           = false;          // makes #hasNext return false
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
