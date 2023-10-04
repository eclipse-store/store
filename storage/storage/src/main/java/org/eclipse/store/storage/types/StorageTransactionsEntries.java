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


import static org.eclipse.serializer.util.X.notNull;

import org.eclipse.serializer.afs.types.AFS;
import org.eclipse.serializer.afs.types.AFile;
import org.eclipse.serializer.afs.types.AReadableFile;
import org.eclipse.serializer.collections.BulkList;
import org.eclipse.serializer.collections.types.XCollection;
import org.eclipse.serializer.collections.types.XGettingSequence;
import org.eclipse.serializer.util.X;
import org.eclipse.store.storage.exceptions.StorageException;


public interface StorageTransactionsEntries
{
	public XGettingSequence<Entry> entries();
	
	
	
	public static StorageTransactionsEntries parseFileContent(final AReadableFile file)
	{
		if(!file.exists())
		{
			return StorageTransactionsEntries.New();
		}
		
		final BulkList<Entry> entries = BulkList.New();
		
		StorageTransactionsAnalysis.Logic.processInputFile(
			file,
			new EntryCollector(entries)
		);
		
		return StorageTransactionsEntries.New(entries);
	}
	
	public static StorageTransactionsEntries parseFile(final AFile file)
	{
		return AFS.apply(file, rf ->
		{
			return parseFileContent(rf);
		});
	}
		
	
	public static StorageTransactionsEntries New()
	{
		return new StorageTransactionsEntries.Default(
				X.empty()
		);
	}
	
	public static StorageTransactionsEntries New(final XGettingSequence<Entry> entries)
	{
		return new StorageTransactionsEntries.Default(
			notNull(entries)
		);
	}
	
	public final class Default implements StorageTransactionsEntries
	{
		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////
		
		final XGettingSequence<StorageTransactionsEntries.Entry> entries;

		
		
		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////
		
		Default(final XGettingSequence<Entry> entries)
		{
			super();
			this.entries = entries;
		}


		
		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////

		@Override
		public final XGettingSequence<Entry> entries()
		{
			return this.entries;
		}
		
	}
	
	

	public interface Entry
	{
		public StorageTransactionsEntryType type();
		
		public long timestamp();

		public long fileLength();

		public long targetFileNumber();
		
		public Long sourceFileNumber();
		
		public Long specialOffset();
		
		public long lengthChange();
		
		public void setLengthChange(long lengthChange);
		
		
		public static Entry New(
			final StorageTransactionsEntryType type            ,
			final long      timestamp       ,
			final long      fileLength      ,
			final long      targetFileNumber,
			final Long      sourceFileNumber,
			final Long      specialOffset
		)
		{
			// no constraints to allow inventorying of any transactions file, potentially inconsistent.
			return new Entry.Default(
				type            ,
				timestamp       ,
				fileLength      ,
				targetFileNumber,
				sourceFileNumber,
				specialOffset
			);
		}
		
		public final class Default implements Entry
		{
			///////////////////////////////////////////////////////////////////////////
			// instance fields //
			////////////////////
			
			private final StorageTransactionsEntryType type            ;
			private final long      timestamp       ;
			private final long      fileLength      ;
			private final long      targetFileNumber;
			private final Long      sourceFileNumber;
			private final Long      specialOffset   ;
			
			private       long      lengthChange    ;
			
			
			
			///////////////////////////////////////////////////////////////////////////
			// constructors //
			/////////////////
			
			Default(
				final StorageTransactionsEntryType type            ,
				final long      timestamp       ,
				final long      fileLength      ,
				final long      targetFileNumber,
				final Long      sourceFileNumber,
				final Long      specialOffset
			)
			{
				super();
				this.type             = type            ;
				this.timestamp        = timestamp       ;
				this.fileLength       = fileLength      ;
				this.targetFileNumber = targetFileNumber;
				this.sourceFileNumber = sourceFileNumber;
				this.specialOffset    = specialOffset   ;
			}
			
			
			
			///////////////////////////////////////////////////////////////////////////
			// methods //
			////////////

			@Override
			public final StorageTransactionsEntryType type()
			{
				return this.type;
			}

			@Override
			public final long timestamp()
			{
				return this.timestamp;
			}

			@Override
			public final long fileLength()
			{
				return this.fileLength;
			}
			
			@Override
			public final long targetFileNumber()
			{
				return this.targetFileNumber;
			}

			@Override
			public final Long sourceFileNumber()
			{
				return this.sourceFileNumber;
			}

			@Override
			public final Long specialOffset()
			{
				return this.specialOffset;
			}
			
			@Override
			public long lengthChange()
			{
				return this.lengthChange;
			}
			
			@Override
			public void setLengthChange(final long lengthChange)
			{
				this.lengthChange = lengthChange;
			}
			
			@Override
			public final String toString()
			{
				return this.type + " time=" + this.timestamp + ", fileLength=" + this.fileLength;
			}
			
		}
		
	}
	

	public final class EntryCollector implements StorageTransactionsAnalysis.EntryIterator
	{
		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////
		
		private final XCollection<Entry> entries;
		
		private long currentFileNumber = 0;
		private long currentFileLength;
		
		
		
		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////

		EntryCollector(final XCollection<Entry> entries)
		{
			super();
			this.entries = entries;
		}
		
		
		
		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////

		@Override
		public boolean accept(final long address, final long availableEntryLength)
		{
			// check for and skip gaps / comments
			if(availableEntryLength < 0)
			{
				return true;
			}

			switch(StorageTransactionsAnalysis.Logic.getEntryType(address))
			{
				case StorageTransactionsAnalysis.Logic.TYPE_FILE_CREATION  : return this.parseEntryFileCreation  (address, availableEntryLength);
				case StorageTransactionsAnalysis.Logic.TYPE_STORE          : return this.parseEntryStore         (address, availableEntryLength);
				case StorageTransactionsAnalysis.Logic.TYPE_TRANSFER       : return this.parseEntryTransfer      (address, availableEntryLength);
				case StorageTransactionsAnalysis.Logic.TYPE_FILE_TRUNCATION: return this.parseEntryFileTruncation(address, availableEntryLength);
				case StorageTransactionsAnalysis.Logic.TYPE_FILE_DELETION  : return this.parseEntryFileDeletion  (address, availableEntryLength);
				default:
				{
					throw new StorageException("Unknown transactions entry type: " + StorageTransactionsAnalysis.Logic.getEntryType(address));
				}
			}
		}
		
		private boolean parseEntryFileCreation(final long address, final long availableEntryLength)
		{
			if(availableEntryLength < StorageTransactionsAnalysis.Logic.LENGTH_FILE_CREATION)
			{
				return false;
			}
			
			final Entry e = Entry.New(
				StorageTransactionsEntryType.FILE_CREATION         ,
				StorageTransactionsAnalysis.Logic.getEntryTimestamp(address),
				StorageTransactionsAnalysis.Logic.getFileLength    (address),
				StorageTransactionsAnalysis.Logic.getFileNumber    (address),
				this.currentFileNumber          ,
				null
			);
			this.currentFileNumber = e.targetFileNumber();
			this.currentFileLength = 0;
			this.addEntry(e);
			
			return true;
			
		}
		
		private void addEntry(final Entry e)
		{
			e.setLengthChange(e.fileLength() - this.currentFileLength);
			this.currentFileLength = e.fileLength();
			
			this.entries.add(e);
		}
		
		private boolean parseEntryStore(final long address, final long availableEntryLength)
		{
			if(availableEntryLength < StorageTransactionsAnalysis.Logic.LENGTH_STORE)
			{
				return false;
			}
			
			final Entry e = Entry.New(
				StorageTransactionsEntryType.DATA_STORE            ,
				StorageTransactionsAnalysis.Logic.getEntryTimestamp(address),
				StorageTransactionsAnalysis.Logic.getFileLength    (address),
				this.currentFileNumber          ,
				null,
				null
			);
			this.addEntry(e);
			
			return true;
		}
		
		private boolean parseEntryTransfer(final long address, final long availableEntryLength)
		{
			if(availableEntryLength < StorageTransactionsAnalysis.Logic.LENGTH_TRANSFER)
			{
				return false;
			}
			
			final Entry e = Entry.New(
				StorageTransactionsEntryType.DATA_TRANSFER         ,
				StorageTransactionsAnalysis.Logic.getEntryTimestamp(address),
				StorageTransactionsAnalysis.Logic.getFileLength    (address),
				this.currentFileNumber          ,
				StorageTransactionsAnalysis.Logic.getFileNumber    (address),
				StorageTransactionsAnalysis.Logic.getSpecialOffset (address)
			);
			this.addEntry(e);
			
			return true;
		}
		
		private boolean parseEntryFileTruncation(final long address, final long availableEntryLength)
		{
			if(availableEntryLength < StorageTransactionsAnalysis.Logic.LENGTH_FILE_TRUNCATION)
			{
				return false;
			}
			
			final Entry e = Entry.New(
				StorageTransactionsEntryType.FILE_TRUNCATION       ,
				StorageTransactionsAnalysis.Logic.getEntryTimestamp(address),
				StorageTransactionsAnalysis.Logic.getFileLength    (address),
				StorageTransactionsAnalysis.Logic.getFileNumber    (address),
				null                            ,
				StorageTransactionsAnalysis.Logic.getSpecialOffset (address)
			);
			this.addEntry(e);
			
			return true;
		}
		
		private boolean parseEntryFileDeletion(final long address, final long availableEntryLength)
		{
			if(availableEntryLength < StorageTransactionsAnalysis.Logic.LENGTH_FILE_DELETION)
			{
				return false;
			}
			
			final Entry e = Entry.New(
				StorageTransactionsEntryType.FILE_DELETION         ,
				StorageTransactionsAnalysis.Logic.getEntryTimestamp(address),
				StorageTransactionsAnalysis.Logic.getFileLength    (address),
				StorageTransactionsAnalysis.Logic.getFileNumber    (address),
				null                            ,
				null
			);
			
			// no changing of current file number or length by a delete!
			this.entries.add(e);
			
			return true;
		}
		
	}


}
