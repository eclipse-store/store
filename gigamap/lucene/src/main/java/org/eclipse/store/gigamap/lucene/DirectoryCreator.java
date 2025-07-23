package org.eclipse.store.gigamap.lucene;

/*-
 * #%L
 * EclipseStore GigaMap Lucene
 * %%
 * Copyright (C) 2023 - 2025 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.MMapDirectory;
import org.eclipse.serializer.exceptions.IORuntimeException;

import java.io.IOException;
import java.nio.file.Path;

import static org.eclipse.serializer.util.X.notNull;

/**
 * DirectoryCreator is an abstract class designed to encapsulate the creation
 * of different types of {@link Directory} instances. Subclasses of DirectoryCreator
 * provide specific implementations for the {@link Directory} creation process.
 * <p>
 * The primary purpose of this class is to provide a uniform mechanism for
 * creating Directory objects while delegating the specifics of the creation
 * to its subclasses. This abstraction helps isolate the logic for different
 * types of Directory creation.
 * <p>
 * Subclasses:
 * - MMapDirectoryCreator: Creates an instance of a memory-mapped Directory.
 * - ByteBuffersDirectoryCreator: Creates an instance of a byte-buffer-based Directory.
 * <p>
 * Note: This class is intentionally not a functional interface to avoid
 * potential issues with the use of unpersistable lambda instances.
 */
//may NOT be a functional interface to avoid unpersistable lambda instances getting used.
public abstract class DirectoryCreator
{
	/**
	 * Creates and returns a new instance of a {@link Directory}.
	 * The specific type of {@link Directory} to be created is determined
	 * by the concrete implementation of this abstract method in subclasses
	 * of {@link DirectoryCreator}.
	 *
	 * @return a newly created instance of {@link Directory}.
	 */
	public abstract Directory createDirectory();
	
	/**
	 * Creates an instance of {@link MMapDirectoryCreator}, a specific implementation
	 * of {@link DirectoryCreator} that constructs memory-mapped {@link Directory} instances.
	 * The memory-mapped directory persists data to the file system using the provided {@link Path}.
	 *
	 * @param path the file system path where the memory-mapped directory will store its data;
	 *             must not be null.
	 * @return an instance of {@link MMapDirectoryCreator}, which provides
	 *         the functionality to create a memory-mapped directory at the specified path.
	 */
	public static DirectoryCreator MMap(final Path path)
	{
		return new MMapDirectoryCreator(
			notNull(path)
		);
	}
	
	/**
	 * Returns an instance of {@link ByteBuffersDirectoryCreator},
	 * a specific implementation of {@link DirectoryCreator} that creates a byte-buffer-based {@link Directory}.
	 * <p>
	 * Keep in mind that this is a transient directory. Its state will not be persisted.
	 * If you want a persistent state, use {@link #MMap(Path)} instead.
	 *
	 * @return an instance of {@link ByteBuffersDirectoryCreator},
	 *         which provides the functionality to create a {@link ByteBuffersDirectory}.
	 */
	public static DirectoryCreator ByteBuffers()
	{
		return new ByteBuffersDirectoryCreator();
	}
	
	
	
	public static class MMapDirectoryCreator extends DirectoryCreator
	{
		private final Path path;

		MMapDirectoryCreator(final Path path)
		{
			super();
			this.path = path;
		}
	
		@Override
		public Directory createDirectory()
		{
			try
			{
				return new MMapDirectory(this.path);
			}
			catch(final IOException e)
			{
				throw new IORuntimeException(e);
			}
		}
		
	}
	
	
	public static class ByteBuffersDirectoryCreator extends DirectoryCreator
	{
		ByteBuffersDirectoryCreator()
		{
			super();
		}
		
		@Override
		public Directory createDirectory()
		{
			return new ByteBuffersDirectory();
		}
		
	}
	
}
