package org.eclipse.store.gigamap.types;

/**
 * An {@link EntityIdMatcher} that exposes the result of a bitmap-based query as a matcher,
 * so that the query can participate as a {@link GigaMap.SubQuery} in another query.
 * <p>
 * Internally the matcher drives an {@link AbstractBitmapIterating} traversal over the given
 * {@link BitmapResult}s. Because this traversal proceeds in ascending entity-id order, the
 * matcher supports the "next candidate id" contract of {@link #matchEntityId(long)} (case #3),
 * allowing the calling query executor to skip over gaps efficiently.
 * <p>
 * A {@code BitmapEntityIdMatcher} holds read-state on its parent {@link GigaMap} and therefore
 * implements {@link GigaMap.Reading}. Callers must eventually {@link #close() close} it to
 * release the associated read lock.
 *
 * @param <E> the entity type of the parent {@link GigaMap}
 *
 * @see EntityIdMatcher
 * @see GigaMap.SubQuery
 */
public final class BitmapEntityIdMatcher<E> extends AbstractBitmapIterating<E> implements EntityIdMatcher, GigaMap.Reading
{
	///////////////////////////////////////////////////////////////////////////
	// instance fields //
	////////////////////

	// parent must be referenced seperately because resolver might not use/reference it at all.
	private final GigaMap.Default<E> parent;

	private boolean isActive = true;

	private long searchEntityId = -1;
	private long currentEntityId = -1;



	///////////////////////////////////////////////////////////////////////////
	// constructors //
	/////////////////

	BitmapEntityIdMatcher(
		final GigaMap.Default<E> parent   ,
		final EntityIdMatcher    idMatcher,
		final long               idStart  ,
		final long               idBound  ,
		final BitmapResult[]     results
	)
	{
		// currentBitPosition must be -1 due to pre-increment logic
		super(idMatcher, idStart, idBound, results, -1);
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
	public long matchEntityId(final long entityId)
	{
		synchronized(this.parent())
		{
			if(this.isActive)
			{
				this.searchEntityId = entityId;
				this.execute();

				// this is either -1 (no more ids) or the passed entityId or a higher id. In any case, it's correct to return it.
				return this.currentEntityId;
			}

			// Matcher has been closed/deactivated. No more entityIds.
			return -1;
		}

	}

	@Override
	protected final boolean handleEntityId(final long entityId)
	{
		if(entityId < this.searchEntityId)
		{
			// id too small/low, keep searching.
			return false;
		}

		this.currentEntityId = entityId;
		return true;
	}

	@Override
	protected final void terminateLoop()
	{
		this.currentEntityId = -1;
	}

	/**
	 * Closes this matcher, releasing the read lock held on its parent {@link GigaMap}.
	 * <p>
	 * After closing, {@link #matchEntityId(long)} will return {@code -1L} for every id.
	 */
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
