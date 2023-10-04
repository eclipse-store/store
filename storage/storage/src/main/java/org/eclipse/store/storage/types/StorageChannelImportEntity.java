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

public interface StorageChannelImportEntity
{
	public int length();

	public StorageEntityType.Default type();

	public long objectId();
	
	public StorageChannelImportEntity next();
	
	
	
	public static class Default implements StorageChannelImportEntity
	{
		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////

		final int                                length  ;
		final long                               objectId;
		final StorageEntityType.Default          type    ;
		      StorageChannelImportEntity.Default next    ;

		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////

		Default(
			final int                       length  ,
			final long                      objectId,
			final StorageEntityType.Default type
		)
		{
			super();
			this.length   = length  ;
			this.objectId = objectId;
			this.type     = type    ;
		}

		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////

		@Override
		public final int length()
		{
			return this.length;
		}

		@Override
		public final StorageEntityType.Default type()
		{
			return this.type;
		}

		@Override
		public final long objectId()
		{
			return this.objectId;
		}

		@Override
		public final StorageChannelImportEntity.Default next()
		{
			return this.next;
		}

	}
	
}
