package org.eclipse.store.storage.types;

import org.eclipse.store.storage.types.StorageChannel.HousekeepingTask;

public interface StorageChannelHousekeepingResult
{
	long getDuration();

	long getStartTime();

	boolean getResult();
	
	long getBudget();

	public static StorageChannelHousekeepingResult create(long nanoTimeBudget, final HousekeepingTask task)
	{
		final long startTime = System.currentTimeMillis();
		final long startTimeStamp = System.nanoTime();
		final boolean result = task.perform();
		final long duration = System.nanoTime() - startTimeStamp;
		
		return new Default(nanoTimeBudget, result, startTime, duration);
	}
	
	public final class Default implements StorageChannelHousekeepingResult
	{
		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////
		
		private long nanoTimeBudget;
		private final boolean result;
		private final long startTime;
		private final long duration;
		
		
		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////
		
		private Default(long nanoTimeBudget, final boolean result, final long startTime, final long duration)
		{
			super();
			this.nanoTimeBudget = nanoTimeBudget;
			this.result = result;
			this.startTime = startTime;
			this.duration = duration;
		}
		
		
		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////
	
		@Override
		public long getBudget()
		{
			return this.nanoTimeBudget;
		}
		
		@Override
		public boolean getResult()
		{
			return this.result;
		}
	
		@Override
		public long getStartTime()
		{
			return this.startTime;
		}
	
		@Override
		public long getDuration()
		{
			return this.duration;
		}

	}
	
}
