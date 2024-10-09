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

import java.nio.ByteBuffer;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.serializer.afs.types.AFile;
import org.eclipse.serializer.afs.types.AFileSystem;
import org.eclipse.serializer.chars.VarString;
import org.eclipse.serializer.chars.XChars;
import org.eclipse.serializer.collections.ArrayView;
import org.eclipse.serializer.collections.XArrays;
import org.eclipse.serializer.memory.XMemory;
import org.eclipse.serializer.util.X;
import org.eclipse.serializer.util.logging.Logging;
import org.eclipse.store.storage.exceptions.StorageException;
import org.slf4j.Logger;

/**
 * The StorageLockFileManager purpose is to provide a mechanism that prevents
 * other storage instances running in different processes from accessing the
 * storage data.
 */
public interface StorageLockFileManager extends Runnable
{
	/**
	 * Start periodical lock file checks.
	 * 
	 * @return this
	 */
	public StorageLockFileManager start();
	
	/**
	 * Stop periodical lock file checks
	 * 
	 * @return this
	 */
	public StorageLockFileManager stop();
	
	/**
	 * Initialize the lock file manager without starting any periodical actions.
	 */
	public void initialize();
	
	/**
	 * Check if the StorageLockFileManager has been successfully initialized and is ready to start.
	 * 
	 * @return true if {@link #initialize()} was successfully executed.
	 */
	public boolean isInitialized();
		
	
	public static StorageLockFileManager New(
		final StorageLockFileSetup                 setup              ,
		final StorageOperationController           operationController,
		final StorageLockFileManagerThreadProvider threadProvider
	)
	{
		return new StorageLockFileManager.Default(
			notNull(setup),
			notNull(operationController),
			notNull(threadProvider)
		);
	}
	
	/**
	 * Default implementation of the #StorageLockFileManager.
	 * This implementation uses a file to indicate if the storage data
	 * is already in use by a storage instance.
	 * The file contains a process depended ID and time-stamps of the last modification
	 * and expiring time.
	 * A storage is accessible if:
	 * - no lock file exists
	 * - a lock file exists and the process id matches
	 * - a lock file exists and the current system time is greater than the expiring time + update interval
	 */
	public final class Default implements StorageLockFileManager
	{
		private final static Logger logger = Logging.getLogger(StorageLockFileManager.class);
		
		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////
		
		private final StorageLockFileSetup                 setup              ;
		private final StorageOperationController           operationController;
		
		// cached values
		private transient StorageLockFile       lockFile                ;
		private transient LockFileData          lockFileData            ;
		private transient ByteBuffer[]          wrappedByteBuffer       ;
		private transient ArrayView<ByteBuffer> wrappedWrappedByteBuffer;
		private transient ByteBuffer            directByteBuffer        ;
		private transient byte[]                stringReadBuffer        ;
		private transient byte[]                stringWriteBuffer       ;
		private transient VarString vs                                  ;
		private transient AFileSystem fileSystem                        ;
		private transient ScheduledExecutorService executor             ;
		
		
		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////
		
		Default(
			final StorageLockFileSetup                 setup              ,
			final StorageOperationController           operationController,
			final StorageLockFileManagerThreadProvider threadProvider
		)
		{
			super();
			this.setup               = setup              ;
			this.fileSystem          = setup.lockFileProvider().fileSystem();
			this.operationController = operationController;
			this.vs                  = VarString.New()    ;
			// 2 timestamps with separators and an identifier. Should suffice.
			this.wrappedByteBuffer = new ByteBuffer[1];
			this.wrappedWrappedByteBuffer = X.ArrayView(this.wrappedByteBuffer);

			this.stringReadBuffer = new byte[64];
			this.stringWriteBuffer = this.stringReadBuffer.clone();
			this.allocateBuffer(this.stringReadBuffer.length);
			
			this.executor = Executors.newSingleThreadScheduledExecutor(threadProvider);
		}
		
		
		
		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////

		
		@Override
		public final synchronized boolean isInitialized()
		{
			return this.lockFile != null;
		}
		
		private synchronized boolean isReady()
		{
			boolean result = this.isInitialized() && this.operationController.checkProcessingEnabled();
			logger.trace("Storage LockFile Manager isReady: {}" , result);
			return result;
		}
		
		@Override
		public StorageLockFileManager.Default start()
		{
			logger.info("Starting log file manager thread ");
			this.executor.scheduleWithFixedDelay(this, 0, this.setup.updateInterval(), TimeUnit.MICROSECONDS);
			return this;
		}

		@Override
		public final void run()
		{
			try
			{
				if(this.isReady())
				{
					this.updateFile();
				}
				else
				{
					logger.error("Lock File Manager is not ready!");
				}
			}
			catch(final Exception e)
			{
				this.stop();
				this.operationController.registerDisruption(e);
				throw e;
			}
		}
		
		@Override
		public StorageLockFileManager stop()
		{
			if(this.executor.isShutdown()) return this;
			
			this.executor.shutdown();
			try
			{
				if(!this.executor.awaitTermination(100, TimeUnit.MILLISECONDS))
				{
					this.executor.shutdownNow();
					if (!this.executor.awaitTermination(100, TimeUnit.MILLISECONDS))
					{
						logger.error("Failed to shutdown StorageLockFileManager Service executor!");
					}
				}
			}
			catch(InterruptedException e)
			{
				this.executor.shutdownNow();
				Thread.currentThread().interrupt();
			}
			finally
			{
				this.ensureClosedLockFile(null);
			}
			
			logger.info("Storage Lock File Manager stopped");
			
			return this;
		}
		
		/**
		 * Initialize the storage lock file manager without starting the periodical
		 * lock file check.
		 */
		@Override
		public void initialize()
		{
			logger.info("initializing lock file manager for storage {}", this.setup.processIdentity());
			
			final StorageLiveFileProvider fileProvider = this.setup.lockFileProvider();
			final AFile lockFile     = fileProvider.provideLockFile();
			this.lockFile = StorageLockFile.New(lockFile);
			
			
			LockFileData initialFileData = null;
			if(this.lockFileHasContent()) {
				
				initialFileData = this.readLockFileData();
				if(!this.validateExistingLockFileData(initialFileData))
				{
					// wait one interval and try a second time
					logger.warn("Non expired storage lock found! Retrying once");
					
					ScheduledFuture<Boolean> future = this.executor.schedule(() ->
						this.validateExistingLockFileData(this.readLockFileData()),
						initialFileData.updateInterval,
						TimeUnit.MILLISECONDS);
									
					try {
						if(!future.get(initialFileData.updateInterval *2, TimeUnit.MILLISECONDS)) {
							this.executor.shutdownNow();
							throw new StorageException("Storage already in use by: " + initialFileData.identifier);
						}
					} catch(InterruptedException | ExecutionException | TimeoutException e) {
						this.executor.shutdownNow();
						throw new StorageException("failed to validate lock file", e);
					}
				}
				
				
			}
			
			if(this.isReadOnlyMode())
			{
				if(initialFileData != null)
				{
					// write buffer must be filled with the file's current content so the check will be successful.
					this.setToWriteBuffer(initialFileData);
				}
				
				// abort, since neither lockFileData nor writing is required/allowed in read-only mode.
				return;
			}

			this.lockFileData = new LockFileData(this.setup.processIdentity(), this.setup.updateInterval());
			
			this.lockFile.file().ensureExists();
			this.writeLockFileData();
		}
		
		
		private boolean validateExistingLockFileData(final LockFileData lockFileData)
		{
					
			final String identifier = this.setup.processIdentity();
			if(identifier.equals(lockFileData.identifier))
			{
				// database is already owned by "this" process (e.g. crash shorty before), so just continue and reuse.
				logger.info("Storage already owned by process!");
				return true;
			}
			
			if(lockFileData.isLongExpired())
			{
				/*
				 * The lock file is no longer updated, meaning the database is not used anymore
				 * and the lockfile is just a zombie, probably left by a crash.
				 */
				logger.info("Storage lock file outdated, aquiring storage!");
				return true;
			}
			
			logger.debug("Storage lock file not validated! Owner {}, expire time {}", lockFileData.identifier, lockFileData.expirationTime);
			
			return false;
		}
						
		private ByteBuffer ensureReadingBuffer(final int fileLength)
		{
			this.ensureBufferCapacity(fileLength);
			if(this.stringReadBuffer.length != fileLength)
			{
				this.stringReadBuffer = new byte[fileLength];
			}
			
			this.directByteBuffer.clear();
			
			return this.directByteBuffer;
		}
		
		private ArrayView<ByteBuffer> ensureWritingBuffer(final byte[] bytes)
		{
			this.ensureBufferCapacity(bytes.length);
			this.directByteBuffer.clear();
			
			this.stringWriteBuffer = bytes;
			
			return this.wrappedWrappedByteBuffer;
		}
		
		private boolean ensureBufferCapacity(final int capacity)
		{
			if(this.directByteBuffer.capacity() >= capacity)
			{
				// already enough capacity
				return false;
			}
			
			/* data has to be copied multiple times in JDK to load a single String.
			 * Note that using a byte[]-wrapping ByteBuffer is not better since
			 * internally a TemporaryDirectBuffer is used for non-direct buffers that adds the copying step anyway.
			 * The only reasonable thing to use with nio is the DirectByteBuffer.
			 */
			XMemory.deallocateDirectByteBuffer(this.directByteBuffer);
			this.allocateBuffer(capacity);
			
			return true;
		}
		
		private void allocateBuffer(final int capacity)
		{
			this.wrappedByteBuffer[0] = this.directByteBuffer = XMemory.allocateDirectNative(capacity);
		}
		
		private String readString()
		{
			this.fillReadBufferFromFile();
			
			return new String(this.stringReadBuffer, this.setup.charset());
		}
		
		private void fillReadBufferFromFile()
		{
			final int fileLength = X.checkArrayRange(this.lockFile.size());
			this.lockFile.readBytes(this.ensureReadingBuffer(fileLength), 0, fileLength);
			XMemory.copyRangeToArray(XMemory.getDirectByteBufferAddress(this.directByteBuffer), this.stringReadBuffer);
		}
		
		private LockFileData readLockFileData()
		{
			final String currentFileData = this.readString();
			
			// since JDK 9's String change, there's even one more copying required.
			final char[] chars = currentFileData.toCharArray();
			
			final int sep1Index = indexOfFirstNonNumberCharacter(chars, 0);
			final int sep2Index = indexOfFirstNonNumberCharacter(chars, sep1Index + 1);
			
			final long   currentTime    = XChars.parse_longDecimal(chars, 0, sep1Index);
			final long   expirationTime = XChars.parse_longDecimal(chars, sep1Index + 1, sep2Index - sep1Index - 1);
			final String identifier     = String.valueOf(chars, sep2Index + 1, chars.length - sep2Index - 1);
			
			return new LockFileData(currentTime, expirationTime, identifier);
		}
		
		static final int indexOfFirstNonNumberCharacter(final char[] data, final int offset)
		{
			for(int i = offset; i < data.length; i++)
			{
				if(data[i] < '0' || data[i] > '9')
				{
					return i;
				}
			}
			
			throw new StorageException("No separator found in lock file string.");
		}
		
		static final class LockFileData
		{
			      long   lastWriteTime ;
			      long   expirationTime;
			final String identifier    ;
			final long   updateInterval;
			
			LockFileData(final String identifier, final long updateInterval)
			{
				super();
				this.identifier     = identifier    ;
				this.updateInterval = updateInterval;
			}
			
			LockFileData(final long lastWriteTime, final long expirationTime, final String identifier)
			{
				this(identifier, deriveUpdateInterval(lastWriteTime, expirationTime));
				this.lastWriteTime  = lastWriteTime ;
				this.expirationTime = expirationTime;
			}
			
			final void update()
			{
				this.lastWriteTime  = System.currentTimeMillis();
				this.expirationTime = this.lastWriteTime + this.updateInterval;
			}
			
			private static long deriveUpdateInterval(final long lastWriteTime, final long expirationTime)
			{
				final long derivedInterval = expirationTime - lastWriteTime;
				if(derivedInterval <= 0)
				{
					throw new StorageException(
						"Invalid lockfile timestamps: lastWriteTime = " + lastWriteTime
						+ ", expirationTime = " + expirationTime
					);
				}
				
				return derivedInterval;
			}
			
			/**
			 * "long" meaning the expiration time has been passed by another interval.
			 * This is a tolerance / grace time strategy to exclude
			 */
			final boolean isLongExpired()
			{
				return System.currentTimeMillis() > this.expirationTime + this.updateInterval;
			}
			
		}
		
		private boolean lockFileHasContent()
		{
			return this.lockFile.exists() && this.lockFile.size() > 0;
		}
		
		
		private void checkForModifiedLockFile()
		{
			if(this.isReadOnlyMode() && !this.lockFileHasContent())
			{
				// no existing lock file can be ignored in read-only mode.
				return;
			}
			
			this.fillReadBufferFromFile();
			
			// performance-optimized JDK method
			if(XArrays.equals(this.stringReadBuffer, this.stringWriteBuffer, this.stringWriteBuffer.length))
			{
				return;
			}

			throw new StorageException("Concurrent lock file modification detected.");
		}
		
		private boolean isReadOnlyMode()
		{
			return !this.fileSystem.isWritable();
		}
		
		private void writeLockFileData()
		{
			if(this.isReadOnlyMode())
			{
				// do not write in read-only mode. But everything else (modification checking etc.) is still required.
				return;
			}
			
			this.lockFileData.update();
						
			final ArrayView<ByteBuffer> bb = this.setToWriteBuffer(this.lockFileData);
			
			// no need for the writer detour (for now) since it makes no sense to backup lock files.
			
			//don't delete file!
			this.lockFile.truncate(0);
			this.lockFile.writeBytes(bb);
			
		}
		
		private ArrayView<ByteBuffer> setToWriteBuffer(final LockFileData lockFileData)
		{
			this.vs.reset()
			.add(lockFileData.lastWriteTime).add(';')
			.add(lockFileData.expirationTime).add(';')
			.add(lockFileData.identifier)
			;
			
			final byte[] bytes = this.vs.encodeBy(this.setup.charset());
			final ArrayView<ByteBuffer> bb = this.ensureWritingBuffer(bytes);
			
			XMemory.copyArrayToAddress(bytes, XMemory.getDirectByteBufferAddress(this.directByteBuffer));
			this.directByteBuffer.limit(bytes.length);
			
			return bb;
		}
				
		private void updateFile()
		{
			logger.trace("updating lock file");
			
			this.checkForModifiedLockFile();
			this.writeLockFileData();
		}
		
		private void ensureClosedLockFile(final Throwable cause)
		{
			if(this.lockFile == null)
			{
				return;
			}
		
			logger.debug("closing lockfile!");
			StorageClosableFile.close(this.lockFile, cause);
			this.lockFile = null;
		}
		
	}
	
	
	public static Creator Creator()
	{
		return new Creator.Default();
	}
	
	public interface Creator
	{
		public StorageLockFileManager createLockFileManager(
			StorageLockFileSetup                 setup              ,
			StorageOperationController           operationController,
			StorageLockFileManagerThreadProvider threadProvider
		);
		
		public final class Default implements StorageLockFileManager.Creator
		{
			Default()
			{
				super();
			}

			@Override
			public StorageLockFileManager createLockFileManager(
				final StorageLockFileSetup                 setup              ,
				final StorageOperationController           operationController,
				final StorageLockFileManagerThreadProvider threadProvider
			)
			{
				return StorageLockFileManager.New(
					setup              ,
					operationController,
					threadProvider
				);
			}
			
		}
		
	}
	
}
