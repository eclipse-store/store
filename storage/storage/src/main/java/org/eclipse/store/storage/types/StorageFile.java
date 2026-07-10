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

import java.nio.ByteBuffer;

import org.eclipse.serializer.afs.types.AFile;
import org.eclipse.serializer.afs.types.AReadableFile;
import org.eclipse.serializer.afs.types.AWritableFile;
import org.eclipse.serializer.chars.VarString;
import org.eclipse.serializer.chars.XChars;
import org.eclipse.serializer.io.BufferProvider;
import org.eclipse.store.storage.exceptions.StorageException;
import org.eclipse.store.storage.exceptions.StorageExceptionIoReading;
import org.eclipse.store.storage.exceptions.StorageExceptionIoWriting;

/**
 * Common abstraction over the storage-managed files that wrap an {@link AFile} (data files,
 * transactions log files, lock files).
 * <p>
 * The interface bundles a {@link #file() handle to the underlying AFile} with the read/write/copy
 * operations the storage uses internally; mutating operations may throw a
 * {@link org.eclipse.store.storage.exceptions.StorageExceptionIoReading} or
 * {@link org.eclipse.store.storage.exceptions.StorageExceptionIoWriting} on I/O failure rather
 * than a generic {@link java.io.IOException}. Subtypes such as {@link StorageDataFile},
 * {@link StorageLockFile}, and the various {@code StorageBackup*File} types refine the contract for
 * specific roles.
 *
 * @see StorageDataFile
 * @see StorageLockFile
 */
public interface StorageFile
{
	/**
	 * Returns a stable string identifier for this file, by default the {@link #file() underlying file}'s
	 * path string.
	 *
	 * @return a stable string identifying this file.
	 */
	public default String identifier()
	{
		return this.file().toPathString();
	}

	/**
	 * Returns the underlying {@link AFile} handle backing this storage file.
	 *
	 * @return the underlying {@link AFile}.
	 */
	public AFile file();

	/**
	 * Returns the current size of this file in bytes.
	 *
	 * @return the file size in bytes.
	 */
	public long size();

	/**
	 * Returns whether this file currently exists in the underlying file system.
	 *
	 * @return {@code true} if the file exists.
	 */
	public boolean exists();


	/**
	 * Reads bytes from the start of the file into the passed buffer.
	 *
	 * @param targetBuffer the buffer to read into.
	 *
	 * @return the number of bytes read.
	 */
	public long readBytes(final ByteBuffer targetBuffer);

	/**
	 * Reads bytes starting at the passed position into the passed buffer.
	 *
	 * @param targetBuffer the buffer to read into.
	 * @param position     the byte offset in the file to start reading at.
	 *
	 * @return the number of bytes read.
	 */
	public long readBytes(final ByteBuffer targetBuffer, final long position);

	/**
	 * Reads up to {@code length} bytes starting at the passed position into the passed buffer.
	 *
	 * @param targetBuffer the buffer to read into.
	 * @param position     the byte offset in the file to start reading at.
	 * @param length       the maximum number of bytes to read.
	 *
	 * @return the number of bytes read.
	 */
	public long readBytes(final ByteBuffer targetBuffer, final long position, final long length);


	/**
	 * Reads bytes from the start of the file into buffers obtained from the passed
	 * {@link BufferProvider}.
	 *
	 * @param bufferProvider the {@link BufferProvider} supplying target buffers.
	 *
	 * @return the number of bytes read.
	 */
	public long readBytes(BufferProvider bufferProvider);

	/**
	 * Reads bytes starting at the passed position into buffers obtained from the passed
	 * {@link BufferProvider}.
	 *
	 * @param bufferProvider the {@link BufferProvider} supplying target buffers.
	 * @param position       the byte offset in the file to start reading at.
	 *
	 * @return the number of bytes read.
	 */
	public long readBytes(BufferProvider bufferProvider, long position);

	/**
	 * Reads up to {@code length} bytes starting at the passed position into buffers obtained from
	 * the passed {@link BufferProvider}.
	 *
	 * @param bufferProvider the {@link BufferProvider} supplying target buffers.
	 * @param position       the byte offset in the file to start reading at.
	 * @param length         the maximum number of bytes to read.
	 *
	 * @return the number of bytes read.
	 */
	public long readBytes(BufferProvider bufferProvider, long position, long length);


	/**
	 * Writes the passed buffers to the end of this file in iteration order.
	 *
	 * @param buffers the buffers to write.
	 *
	 * @return the total number of bytes written.
	 */
	public long writeBytes(Iterable<? extends ByteBuffer> buffers);


	/**
	 * Forces previously written bytes of this file to physical storage (fsync). No-op on backends
	 * that are already durable on write.
	 */
	public void synchronize();


//	public void pull(AWritableFile fileToMove);


	/**
	 * Copies the entire content of this file to the passed target storage file.
	 *
	 * @param target the destination storage file.
	 *
	 * @return the number of bytes copied.
	 */
	public long copyTo(StorageFile target);

	/**
	 * Copies the content of this file starting at the passed source position to the passed target
	 * storage file.
	 *
	 * @param target         the destination storage file.
	 * @param sourcePosition the byte offset in this file to start reading at.
	 *
	 * @return the number of bytes copied.
	 */
	public long copyTo(StorageFile target, long sourcePosition);

	/**
	 * Copies up to {@code length} bytes from the passed source position of this file to the passed
	 * target storage file.
	 *
	 * @param target         the destination storage file.
	 * @param sourcePosition the byte offset in this file to start reading at.
	 * @param length         the maximum number of bytes to copy.
	 *
	 * @return the number of bytes copied.
	 */
	public long copyTo(StorageFile target, long sourcePosition, long length);


	/**
	 * Copies the entire content of this file to the passed writable {@link AFile}.
	 *
	 * @param target the destination {@link AWritableFile}.
	 *
	 * @return the number of bytes copied.
	 */
	public long copyTo(AWritableFile target);

	/**
	 * Copies the content of this file starting at the passed source position to the passed writable
	 * {@link AFile}.
	 *
	 * @param target         the destination {@link AWritableFile}.
	 * @param sourcePosition the byte offset in this file to start reading at.
	 *
	 * @return the number of bytes copied.
	 */
	public long copyTo(AWritableFile target, long sourcePosition);

	/**
	 * Copies up to {@code length} bytes from the passed source position of this file to the passed
	 * writable {@link AFile}.
	 *
	 * @param target         the destination {@link AWritableFile}.
	 * @param sourcePosition the byte offset in this file to start reading at.
	 * @param length         the maximum number of bytes to copy.
	 *
	 * @return the number of bytes copied.
	 */
	public long copyTo(AWritableFile target, long sourcePosition, long length);


	/**
	 * Appends the entire content of the passed readable file to this file.
	 *
	 * @param source the source {@link AReadableFile}.
	 *
	 * @return the number of bytes copied.
	 */
	public long copyFrom(AReadableFile source);

	/**
	 * Appends the content of the passed readable file from the given source position to this file.
	 *
	 * @param source         the source {@link AReadableFile}.
	 * @param sourcePosition the byte offset in {@code source} to start reading at.
	 *
	 * @return the number of bytes copied.
	 */
	public long copyFrom(AReadableFile source, long sourcePosition);

	/**
	 * Appends up to {@code length} bytes of the passed readable file from the given source position
	 * to this file.
	 *
	 * @param source         the source {@link AReadableFile}.
	 * @param sourcePosition the byte offset in {@code source} to start reading at.
	 * @param length         the maximum number of bytes to copy.
	 *
	 * @return the number of bytes copied.
	 */
	public long copyFrom(AReadableFile source, long sourcePosition, long length);


	/**
	 * Deletes this file from the underlying file system.
	 *
	 * @return {@code true} if the file was deleted by this call.
	 */
	public boolean delete();

	/**
	 * Moves this file to the passed destination, atomically if the underlying file system supports
	 * it.
	 *
	 * @param target the destination {@link AWritableFile}.
	 */
	public void moveTo(AWritableFile target);

	/**
	 * Truncates this file to the passed new length, discarding any bytes beyond it.
	 *
	 * @param newLength the new file length in bytes.
	 */
	public void truncate(final long newLength);



	/**
	 * Appends a compact {@code "name[size]"} representation of the passed storage file to the given
	 * {@link VarString}, for use in diagnostic messages.
	 *
	 * @param vs   the {@link VarString} to append to.
	 * @param file the storage file whose name and size shall be appended.
	 *
	 * @return the same {@link VarString}, for fluent chaining.
	 */
	public static VarString assembleNameAndSize(final VarString vs, final StorageFile file)
	{
		return vs.add(file.file().identifier() + "[" + file.file().size() + "]");
	}

	/**
	 * Abstract base implementation of {@link StorageFile} backed by an {@link AFile} with lazy,
	 * synchronized opening of read- and write-access. Concrete subtypes only need to expose
	 * type-specific accessors; all I/O is implemented here.
	 */
	public abstract class Abstract implements StorageFile
	{
		///////////////////////////////////////////////////////////////////////////
		// instance fields //
		////////////////////
		
		private final AFile file;
		
		private AWritableFile writeAccess;
		private AReadableFile readAccess ;
		
		
		///////////////////////////////////////////////////////////////////////////
		// constructors //
		/////////////////
		
		protected Abstract(final AFile file)
		{
			super();
			this.file = file;
		}
		
		
		
		///////////////////////////////////////////////////////////////////////////
		// methods //
		////////////
		
		@Override
		public AFile file()
		{
			return this.file;
		}
		
		@Override
		public final synchronized long size()
		{
			return this.file().size();
		}
		
		@Override
		public final synchronized boolean exists()
		{
			return this.file.exists();
		}
		
			
		@Override
		public final synchronized long readBytes(final ByteBuffer targetBuffer)
		{
			try
			{
				return this.ensureReadable().readBytes(targetBuffer);
			}
			catch(final Exception e)
			{
				throw new StorageExceptionIoReading(e);
			}
		}
		
		@Override
		public final synchronized long readBytes(final ByteBuffer targetBuffer, final long position)
		{
			try
			{
				return this.ensureReadable().readBytes(targetBuffer, position);
			}
			catch(final Exception e)
			{
				throw new StorageExceptionIoReading(e);
			}
		}
		
		@Override
		public final synchronized long readBytes(final ByteBuffer targetBuffer, final long position, final long length)
		{
			try
			{
				return this.ensureReadable().readBytes(targetBuffer, position, length);
			}
			catch(final Exception e)
			{
				throw new StorageExceptionIoReading(e);
			}
		}
		
		@Override
		public final synchronized long readBytes(final BufferProvider bufferProvider)
		{
			try
			{
				return this.ensureReadable().readBytes(bufferProvider);
			}
			catch(final Exception e)
			{
				throw new StorageExceptionIoReading(e);
			}
		}
		
		@Override
		public final synchronized long readBytes(
			final BufferProvider bufferProvider,
			final long           position
		)
		{
			try
			{
				return this.ensureReadable().readBytes(bufferProvider, position);
			}
			catch(final Exception e)
			{
				throw new StorageExceptionIoReading(e);
			}
		}
		
		@Override
		public final synchronized long readBytes(
			final BufferProvider bufferProvider,
			final long           position      ,
			final long           length
		)
		{
			try
			{
				return this.ensureReadable().readBytes(bufferProvider, position, length);
			}
			catch(final Exception e)
			{
				throw new StorageExceptionIoReading(e);
			}
		}
		

		@Override
		public final synchronized long writeBytes(final Iterable<? extends ByteBuffer> buffers)
		{
			try
			{
				return this.ensureWritable().writeBytes(buffers);
			}
			catch(final Exception e)
			{
				throw new StorageExceptionIoWriting(e);
			}
		}

		@Override
		public final synchronized void synchronize()
		{
			try
			{
				this.ensureWritable().synchronize();
			}
			catch(final Exception e)
			{
				throw new StorageExceptionIoWriting(e);
			}
		}

		@Override
		public final synchronized long copyTo(
			final StorageFile target
		)
		{
			return target.copyFrom(this.ensureReadable());
		}
		
		@Override
		public final synchronized long copyTo(
			final StorageFile target        ,
			final long        sourcePosition
		)
		{
			return target.copyFrom(this.ensureReadable(), sourcePosition);
		}

		@Override
		public final synchronized long copyTo(
			final StorageFile target        ,
			final long        sourcePosition,
			final long        length
		)
		{
			return target.copyFrom(this.ensureReadable(), sourcePosition, length);
		}
		
		@Override
		public final synchronized long copyTo(
			final AWritableFile target
		)
		{
			try
			{
				return target.copyFrom(this.ensureReadable());
			}
			catch(final Exception e)
			{
				throw new StorageException(e);
			}
		}
		
		@Override
		public final synchronized long copyTo(
			final AWritableFile target        ,
			final long          sourcePosition
		)
		{
			try
			{
				return target.copyFrom(this.ensureReadable(), sourcePosition);
			}
			catch(final Exception e)
			{
				throw new StorageException(e);
			}
		}

		@Override
		public final synchronized long copyTo(
			final AWritableFile target        ,
			final long          sourcePosition,
			final long          length
		)
		{
			try
			{
				target.ensureExists();
				return target.copyFrom(this.ensureReadable(), sourcePosition, length);
			}
			catch(final Exception e)
			{
				throw new StorageException(e);
			}
		}
				
		@Override
		public final synchronized long copyFrom(
			final AReadableFile source
		)
		{
			try
			{
				return source.copyTo(this.ensureWritable());
			}
			catch(final Exception e)
			{
				throw new StorageException(e);
			}
		}
		
		@Override
		public final synchronized long copyFrom(
			final AReadableFile source        ,
			final long          sourcePosition
		)
		{
			try
			{
				return source.copyTo(this.ensureWritable(), sourcePosition);
			}
			catch(final Exception e)
			{
				throw new StorageException(e);
			}
		}

		@Override
		public final synchronized long copyFrom(
			final AReadableFile source        ,
			final long          sourcePosition,
			final long          length
		)
		{
			try
			{
				return source.copyTo(this.ensureWritable(), sourcePosition, length);
			}
			catch(final Exception e)
			{
				throw new StorageException(e);
			}
		}
				
		@Override
		public synchronized void truncate(final long newLength)
		{
			this.ensureWritable().truncate(newLength);
		}
		
		@Override
		public final synchronized boolean delete()
		{
			return this.ensureWritable().delete();
		}
		
		@Override
		public final synchronized void moveTo(final AWritableFile target)
		{
			this.ensureWritable().moveTo(target);
		}
		
		protected synchronized AReadableFile ensureReadable()
		{
			this.internalOpenReading();
			
			return this.readAccess;
		}
		
		protected synchronized AWritableFile ensureWritable()
		{
			this.internalOpenWriting();
			
			return this.writeAccess;
		}
		
		public synchronized boolean isOpen()
		{
			return this.writeAccess != null && this.writeAccess.isOpen();
		}

		public synchronized boolean close()
		{
			boolean result = false;
			
			if(this.writeAccess != null)
			{
				 result = this.writeAccess.release();
				 this.writeAccess = null;
			}
			
			if(this.readAccess != null )
			{
				result = this.readAccess.release();
				this.readAccess = null;
			}
			
			return result;
		}
		
		protected synchronized boolean internalOpenWriting()
		{
			try
			{
				if(this.writeAccess == null || this.writeAccess.isRetired())
				{
					this.writeAccess = this.file().useWriting();
					this.readAccess = this.writeAccess;
				}
				
				return this.writeAccess.open();
			}
			catch(final Exception e)
			{
				throw new StorageException(e);
			}
		}
		
		protected synchronized boolean internalOpenReading()
		{
			try
			{
				if(this.readAccess == null || this.readAccess.isRetired())
				{
					this.writeAccess = null;
					this.readAccess = this.file().useReading();
				}
				
				return this.readAccess.open();
			}
			catch(final Exception e)
			{
				throw new StorageException(e);
			}
		}
		
		@Override
		public String toString()
		{
			return XChars.systemString(this) + " (" + this.file + ")";
		}
		
	}
	
}
