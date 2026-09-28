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

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The bindings of {@link IndexLocation#Named(String) named index locations}.
 * <p>
 * Bindings are JVM-wide and meant to be set once at application startup, before the storage is started:
 * indices resolve their location whenever they are created or loaded, which includes maps loaded later
 * on other threads (e.g. behind a {@code Lazy}). A binding is therefore not tied to a storage: several
 * storages in one JVM must use different names.
 * <p>
 * An index resolves its location once, when it is created or loaded; rebinding a name affects only
 * indices resolved afterwards. A name that is not bound fails the resolution loudly — there is no
 * fallback.
 */
public final class IndexLocations
{
	///////////////////////////////////////////////////////////////////////////
	// static fields //
	//////////////////

	private static final Map<String, Path> BINDINGS = new ConcurrentHashMap<>();



	///////////////////////////////////////////////////////////////////////////
	// static methods //
	///////////////////

	/**
	 * Binds a name JVM-wide, replacing a previous binding of it.
	 *
	 * @param name      the location name
	 * @param directory the directory the name stands for
	 */
	public static void bind(
		final String name     ,
		final Path   directory
	)
	{
		BINDINGS.put(notNull(name), notNull(directory));
	}

	/**
	 * Removes the binding of a name.
	 *
	 * @param name the location name
	 * @return whether the name was bound
	 */
	public static boolean unbind(final String name)
	{
		return BINDINGS.remove(notNull(name)) != null;
	}

	/**
	 * @param name the location name
	 * @return the directory the name is bound to, or {@code null} if it is unbound
	 */
	public static Path lookup(final String name)
	{
		return BINDINGS.get(notNull(name));
	}

	/**
	 * @param name the location name
	 * @return the directory the name is bound to
	 * @throws IllegalStateException if the name is unbound
	 */
	public static Path resolve(final String name)
	{
		final Path directory = lookup(name);
		if(directory == null)
		{
			throw new IllegalStateException(
				"Index location name \"" + name + "\" is not bound; bind it with IndexLocations.bind(\""
				+ name + "\", directory) before the index is created or loaded."
			);
		}
		return directory;
	}



	///////////////////////////////////////////////////////////////////////////
	// constructors //
	/////////////////

	private IndexLocations()
	{
		// static only
		throw new UnsupportedOperationException();
	}

}
