package org.eclipse.store.gigamap.types;

/*-
 * #%L
 * EclipseStore GigaMap
 * %%
 * Copyright (C) 2023 - 2026 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import static org.eclipse.serializer.util.X.notNull;

import org.eclipse.serializer.persistence.types.Unpersistable;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Where an index keeps files outside the storage, e.g. the graph of an on-disk vector index or the
 * files of a Lucene index in an MMap directory.
 * <ul>
 *   <li>{@link #Absolute(Path)} — a fixed directory, the behaviour of a plain path.</li>
 *   <li>{@link #Named(String)} — a logical name the application binds to a directory at runtime via
 *       {@link IndexLocations#bind(String, Path)}, like a data-source name. The index opens in whatever
 *       directory the name is bound to when it is created or loaded, so a storage that was moved,
 *       restored or copied can be pointed at its index files without changing any stored data.</li>
 * </ul>
 * An {@code IndexLocation} is a value for configuration APIs and is never stored itself: index
 * configurations store it as plain fields (a path and a name), so that a library version that does not
 * know this type can still read the storage.
 */
public interface IndexLocation extends Unpersistable
{
	/**
	 * @return the directory of an absolute location, or {@code null} for a named one
	 */
	public Path directory();

	/**
	 * @return the name of a named location, or {@code null} for an absolute one
	 */
	public String name();

	/**
	 * Resolves the location now: the directory of an absolute location, or the directory the name of a
	 * named location is currently bound to.
	 *
	 * @return the directory, never {@code null}
	 * @throws IllegalStateException if the name of a named location is not bound
	 */
	public Path resolve();


	public static IndexLocation Absolute(final Path directory)
	{
		return new Absolute(notNull(directory));
	}

	public static IndexLocation Named(final String name)
	{
		notNull(name);
		if(name.isBlank())
		{
			throw new IllegalArgumentException("An index location name must not be blank.");
		}
		return new Named(name);
	}


	public final class Absolute implements IndexLocation
	{
		private final Path directory;

		Absolute(final Path directory)
		{
			super();
			this.directory = directory;
		}

		@Override
		public Path directory()
		{
			return this.directory;
		}

		@Override
		public String name()
		{
			return null;
		}

		@Override
		public Path resolve()
		{
			return this.directory;
		}

		@Override
		public boolean equals(final Object other)
		{
			return other instanceof Absolute && ((Absolute)other).directory.equals(this.directory);
		}

		@Override
		public int hashCode()
		{
			return this.directory.hashCode();
		}

		@Override
		public String toString()
		{
			return "Absolute(" + this.directory + ")";
		}
	}

	public final class Named implements IndexLocation
	{
		private final String name;

		Named(final String name)
		{
			super();
			this.name = name;
		}

		@Override
		public Path directory()
		{
			return null;
		}

		@Override
		public String name()
		{
			return this.name;
		}

		@Override
		public Path resolve()
		{
			return IndexLocations.resolve(this.name);
		}

		@Override
		public boolean equals(final Object other)
		{
			return other instanceof Named && ((Named)other).name.equals(this.name);
		}

		@Override
		public int hashCode()
		{
			return Objects.hash(Named.class, this.name);
		}

		@Override
		public String toString()
		{
			return "Named(\"" + this.name + "\")";
		}
	}

}
