package org.eclipse.store.storage.embedded.tools.storage.migrator.mappings;

/*-
 * #%L
 * EclipseStore Storage Embedded Tools Storage Migrator
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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;


/**
 * Singleton table of MicroStream-to-EclipseStore package renames, loaded from the bundled
 * {@code /META-INF/mappings/package.mappings} resource.
 * <p>
 * Each non-empty line of the resource has the form {@code old.package.name = new.package.name} and is
 * stored in declaration order. Use {@link #INSTANCE} to access the table; {@link #newPackage(String)}
 * looks up a single mapping, while iterating yields all mappings as map entries.
 */
public class PackageMappings implements Iterable<Map.Entry<String, String>>
{
	/**
	 * The singleton instance, eagerly initialized from the bundled mapping resource.
	 */
	public final static PackageMappings INSTANCE = new PackageMappings();

	private final Map<String, String> mappings;

	private PackageMappings()
	{
		this.mappings = this.loadMappings();
	}

	/**
	 * Returns the EclipseStore replacement for the given legacy package name, or {@code null} if no mapping
	 * exists.
	 *
	 * @param oldPackage the legacy package name.
	 *
	 * @return the new package name, or {@code null} if {@code oldPackage} is not mapped.
	 */
	public String newPackage(final String oldPackage)
	{
		return this.mappings.get(oldPackage);
	}

	@Override
	public Iterator<Entry<String, String>> iterator()
	{
		return this.mappings.entrySet().iterator();
	}
	
	private Map<String, String> loadMappings()
	{
		final Map<String, String> mappings = new LinkedHashMap<>();
		
		try(final BufferedReader reader = new BufferedReader(new InputStreamReader(
			this.getClass().getResourceAsStream("/META-INF/mappings/package.mappings")
		)))
		{
			String line;
			while((line = reader.readLine()) != null)
			{
				final String[] parts = line.split("=");
				mappings.put(parts[0].trim(), parts[1].trim());
			}
		}
		catch(final IOException ioe)
		{
			throw new RuntimeException(ioe);
		}
		
		return mappings;
	}
	
}
