package org.eclipse.store.storage.types;

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
