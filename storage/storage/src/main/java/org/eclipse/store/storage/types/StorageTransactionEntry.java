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


/**
 * The representation of a data file inside a transactions file, accumulated from multiple transactions entries.
 *
 */
public interface StorageTransactionEntry
{
	public long fileNumber();

	public long length();

	public boolean isDeleted();
	
	public default boolean isEmpty()
	{
		return this.length() == 0L;
	}



	public final class Default implements StorageTransactionEntry
	{
		////////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////

		final long fileNumber;
		final long length    ;

		boolean isDeleted;



		////////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////

		public Default(final long fileNumber, final long length)
		{
			super();
			this.fileNumber = fileNumber;
			this.length     = length    ;
		}



		////////////////////////////////////////////////////////////////////////////
		// methods //
		////////////

		@Override
		public final long fileNumber()
		{
			return this.fileNumber;
		}

		@Override
		public final long length()
		{
			return this.length;
		}

		@Override
		public final boolean isDeleted()
		{
			return this.isDeleted;
		}

	}

}
