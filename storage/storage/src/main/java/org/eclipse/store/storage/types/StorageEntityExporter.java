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

import org.eclipse.serializer.afs.types.AWritableFile;

public interface StorageEntityExporter<E extends StorageEntity>
{
	public void exportEntities(StorageEntityType<E> type, AWritableFile file);

	public void cleanup();




	public final class Default implements StorageEntityExporter<StorageEntity.Default>
	{
		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////

		@Override
		public final void exportEntities(
			final StorageEntityType<StorageEntity.Default> type,
			final AWritableFile                            file
		)
		{
			type.iterateEntities(e ->
				e.exportTo(file)
			);
		}

		@Override
		public final void cleanup()
		{
			// nothing to clean up in simple storage copying implementation
		}

	}

}
