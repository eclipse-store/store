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
import org.eclipse.store.gigamap.types.IndexLocation;
import org.eclipse.store.gigamap.types.IndexLocations;

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
	 * Creates a memory-mapped {@link Directory} creator for the given {@link IndexLocation}.
	 * <p>
	 * {@link IndexLocation#Absolute(Path)} is the same as {@link #MMap(Path)}.
	 * {@link IndexLocation#Named(String)} stores only the name; the directory is looked up in
	 * {@link IndexLocations} when the Lucene index is registered or loaded, and kept for the rest of the
	 * run, so a moved or copied storage can be pointed at its Lucene files by binding the name to their
	 * new directory. The creator itself holds no resolved state, so one creator can be shared by several
	 * maps.
	 * <p>
	 * A named creator stores no path. A library version without named locations can still read the
	 * storage, but fails when it uses the index.
	 *
	 * @param location the location of the Lucene files; must not be null.
	 * @return a memory-mapped directory creator for that location
	 */
	public static DirectoryCreator MMap(final IndexLocation location)
	{
		notNull(location);
		return location.isNamed()
			? new MMapDirectoryCreator(null, location.name())
			: new MMapDirectoryCreator(location.directory())
		;
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

		// Name of an IndexLocation.Named, or null to use path. A plain field rather than a stored
		// IndexLocation: an older library version drops an unknown field when it loads the storage, but
		// fails the whole start on an instance of a class it does not have. null is also what creators
		// stored before this field existed read as, i.e. today's behaviour.
		private final String locationName;

		MMapDirectoryCreator(final Path path)
		{
			this(path, null);
		}

		MMapDirectoryCreator(final Path path, final String locationName)
		{
			super();
			this.path         = path        ;
			this.locationName = locationName;
		}

		/**
		 * @return where this creator keeps the Lucene files
		 */
		public IndexLocation location()
		{
			return this.locationName != null
				? IndexLocation.Named(this.locationName)
				: IndexLocation.Absolute(this.path)
			;
		}

		/**
		 * Returns a creator for another location. For a named location the old path is not kept: a library
		 * version without named locations would open the files there, which miss everything indexed after
		 * the change, and search them without notice. Without a path it fails on the first use instead.
		 *
		 * @param location the new location
		 * @return the new creator
		 */
		public MMapDirectoryCreator withLocation(final IndexLocation location)
		{
			notNull(location);
			return location.isNamed()
				? new MMapDirectoryCreator(null, location.name())
				: new MMapDirectoryCreator(location.directory())
			;
		}

		/**
		 * Resolves the location now and creates the directory there. A {@link LuceneIndex} does not use
		 * this: it resolves its location once when it is registered or loaded and then calls
		 * {@link #createDirectory(Path)}, so that the files stay where they were opened for the whole run.
		 * For an unbound name this throws; there is no fallback to the stored path.
		 */
		@Override
		public Directory createDirectory()
		{
			return this.createDirectory(this.location().resolve());
		}

		/**
		 * Creates the directory in an already resolved location.
		 *
		 * @param directory the resolved directory
		 * @return the memory-mapped directory
		 */
		public Directory createDirectory(final Path directory)
		{
			try
			{
				return new MMapDirectory(notNull(directory));
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
