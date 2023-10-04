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

import org.eclipse.serializer.afs.types.AFile;


public interface StorageImportSourceFile extends StorageImportSource
{
	
	public static class Default
	extends    StorageImportSource.Abstract
	implements StorageImportSourceFile
	{
		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////

		private final ImportSourceFile sourceFile;


		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////

		Default(
			final int                               channelIndex,
			final AFile                             file        ,
			final StorageChannelImportBatch.Default headBatch
		)
		{
			super(headBatch);
			this.sourceFile = new ImportSourceFile(file, channelIndex);
		}



		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////

		@Override
		public long copyTo(final StorageFile target, final long sourcePosition, final long length)
		{
			return this.sourceFile.copyTo(target, sourcePosition, length);
		}

		@Override
		public boolean close()
		{
			return this.sourceFile.close();
		}

		@Override
		public String toString()
		{
			return Integer.toString(this.sourceFile.channelIndex()) + " "
				+ (this.sourceFile.file() == null ? "<Dummy>"  : this.sourceFile.file().toPathString() + " " + this.headBatch)
			;
		}


		static class ImportSourceFile extends StorageChannelFile.Abstract
		{
			ImportSourceFile(final AFile file, final int channelIndex)
			{
				super(file, channelIndex);
			}

		}

	}
	
}
