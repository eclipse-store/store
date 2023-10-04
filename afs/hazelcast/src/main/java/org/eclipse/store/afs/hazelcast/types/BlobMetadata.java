package org.eclipse.store.afs.hazelcast.types;

/*-
 * #%L
 * EclipseStore Abstract File System Hazelcast
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

import static org.eclipse.serializer.chars.XChars.notEmpty;
import static org.eclipse.serializer.math.XMath.notNegative;

public interface BlobMetadata
{
	public String key();

	public long size();


	public static BlobMetadata New(
		final String key ,
		final long   size
	)
	{
		return new BlobMetadata.Default(
			notEmpty   (key ),
			notNegative(size)
		);
	}


	public static class Default implements BlobMetadata
	{
		private final String key ;
		private final long   size;

		Default(
			final String key ,
			final long   size
		)
		{
			super();
			this.key  = key ;
			this.size = size;
		}

		@Override
		public String key()
		{
			return this.key;
		}

		@Override
		public long size()
		{
			return this.size;
		}

	}

}
