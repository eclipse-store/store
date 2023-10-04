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

import org.eclipse.serializer.functional._longProcedure;

public interface StorageRootOidSelector extends _longProcedure
{
	public void reset();

	public long yield();

	public default void resetGlobal()
	{
		this.reset();
	}

	public default void acceptGlobal(final long rootOid)
	{
		this.accept(rootOid);
	}

	public default long yieldGlobal()
	{
		return this.yield();
	}



	public final class Default implements StorageRootOidSelector
	{
		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////
		
		private transient long currentMax;
		
		
		
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
		public final void accept(final long rootOid)
		{
			if(rootOid < this.currentMax)
			{
				return;
			}
			this.currentMax = rootOid;
		}

		@Override
		public final void reset()
		{
			this.currentMax = 0;

		}

		@Override
		public final long yield()
		{
			return this.currentMax;
		}

	}



	public interface Provider
	{
		public StorageRootOidSelector provideRootOidSelector(int channelIndex);



		public final class Default implements StorageRootOidSelector.Provider
		{
			@Override
			public final StorageRootOidSelector provideRootOidSelector(final int channelIndex)
			{
				return new StorageRootOidSelector.Default();
			}

		}

	}

}
