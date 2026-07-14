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

import org.eclipse.serializer.persistence.binary.types.ChunksBuffer;

public interface StorageRequestTaskLoadRoots extends StorageRequestTaskLoad
{
	public final class Default extends StorageRequestTaskLoad.Abstract
	implements StorageRequestTaskLoadRoots
	{
		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////

		Default(final long timestamp, final int channelCount, final StorageOperationController controller)
		{
			super(timestamp, channelCount, controller);
		}



		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////

		@Override
		protected final ChunksBuffer internalProcessBy(final StorageChannel channel)
		{
			// every channel returns the roots instances (in binary form) that he knows of, potentially none at all.
			return channel.collectLoadRoots(this.resultArray());
		}

	}

}
