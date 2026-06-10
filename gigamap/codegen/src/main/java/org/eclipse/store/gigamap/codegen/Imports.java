/*-
 * #%L
 * EclipseStore GigaMap Codegen
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
package org.eclipse.store.gigamap.codegen;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Collects the type references used while building a single generated source file and hands back the
 * simple name to print for each, registering an {@code import} where one is warranted. Types in
 * {@code java.lang}, types in the file's own package, and primitives are referenced by their simple
 * name without an import; the first type claiming a given simple name wins, later clashing types fall
 * back to their fully qualified name.
 */
final class Imports
{
	private final String              packageName;
	private final Map<String, String> bySimpleName = new TreeMap<>();

	Imports(final String packageName)
	{
		this.packageName = packageName;
	}

	/**
	 * Returns the source token to use for the given raw (generics-free) fully qualified type name,
	 * registering an import if appropriate.
	 */
	String ref(final String fqn)
	{
		final int dot = fqn.lastIndexOf('.');
		if(dot < 0)
		{
			return fqn; // primitive or wrapper passed as a bare simple name
		}
		final String simple = fqn.substring(dot + 1);
		final String pkg    = fqn.substring(0, dot);
		if(pkg.equals("java.lang") || pkg.equals(this.packageName))
		{
			return simple;
		}
		final String existing = this.bySimpleName.get(simple);
		if(existing == null)
		{
			this.bySimpleName.put(simple, fqn);
			return simple;
		}
		return existing.equals(fqn) ? simple : fqn;
	}

	/** The imported fully qualified names, ordered alphabetically. */
	Collection<String> imported()
	{
		final List<String> names = new ArrayList<>(this.bySimpleName.values());
		names.sort(null);
		return names;
	}

	@Override
	public String toString()
	{
		final List<String> names = new ArrayList<>(this.bySimpleName.values());
		return String.join(", ", names);
	}
}