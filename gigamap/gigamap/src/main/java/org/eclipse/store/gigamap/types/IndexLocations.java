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
import java.util.function.Supplier;

/**
 * The bindings of {@link IndexLocation#Named(String) named index locations}.
 * <p>
 * Bindings are JVM-wide and meant to be set once at application startup, before the storage is started:
 * indices resolve their location whenever they are created or loaded, which includes maps loaded later
 * on other threads (e.g. behind a {@code Lazy}). Several storages in one JVM use different names.
 * <p>
 * {@link #withBindings(Map, Supplier)} overrides bindings for the current thread only, e.g. for tests or
 * for several storages that use the same stored name side by side.
 * <p>
 * An index resolves its location once, when it is opened; rebinding a name affects only indices opened
 * afterwards. A name that is bound nowhere fails the resolution loudly — there is no fallback.
 */
public final class IndexLocations
{
	private static final Map<String, Path>              BINDINGS = new ConcurrentHashMap<>();
	private static final ThreadLocal<Map<String, Path>> SCOPED   = new ThreadLocal<>();


	/**
	 * Binds a name JVM-wide, replacing a previous binding of it.
	 *
	 * @param name      the location name
	 * @param directory the directory the name stands for
	 */
	public static void bind(final String name, final Path directory)
	{
		BINDINGS.put(notNull(name), notNull(directory));
	}

	/**
	 * Removes the JVM-wide binding of a name.
	 *
	 * @param name the location name
	 * @return whether the name was bound
	 */
	public static boolean unbind(final String name)
	{
		return BINDINGS.remove(notNull(name)) != null;
	}

	/**
	 * Runs an action with bindings that win over the JVM-wide ones, on the current thread only.
	 *
	 * @param <T>      the result type
	 * @param bindings the thread-scoped bindings
	 * @param action   the action
	 * @return the action's result
	 */
	public static <T> T withBindings(final Map<String, Path> bindings, final Supplier<T> action)
	{
		final Map<String, Path> copy     = Map.copyOf(bindings);
		final Map<String, Path> previous = SCOPED.get();
		SCOPED.set(copy);
		try
		{
			return notNull(action).get();
		}
		finally
		{
			if(previous == null)
			{
				SCOPED.remove();
			}
			else
			{
				SCOPED.set(previous);
			}
		}
	}

	/**
	 * @param name the location name
	 * @return the directory the name is bound to for the current thread, or {@code null} if it is unbound
	 */
	public static Path lookup(final String name)
	{
		notNull(name);
		final Map<String, Path> scoped = SCOPED.get();
		if(scoped != null)
		{
			final Path directory = scoped.get(name);
			if(directory != null)
			{
				return directory;
			}
		}
		return BINDINGS.get(name);
	}

	/**
	 * @param name the location name
	 * @return the directory the name is bound to for the current thread
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


	private IndexLocations()
	{
		// static only
		throw new UnsupportedOperationException();
	}
}
