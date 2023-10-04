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


public interface StorageRootTypeIdProvider
{
	public long provideRootTypeId();



	public final class Default implements StorageRootTypeIdProvider
	{
		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////

		private final long rootTypeId;



		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////

		public Default(final long rootTypeId)
		{
			super();
			this.rootTypeId = rootTypeId;
		}



		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////

		@Override
		public final long provideRootTypeId()
		{
			return this.rootTypeId;
		}

	}

}
