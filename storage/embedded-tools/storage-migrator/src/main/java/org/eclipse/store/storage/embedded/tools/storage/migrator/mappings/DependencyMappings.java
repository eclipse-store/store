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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;


public class DependencyMappings implements Iterable<DependencyMapping>
{
	public final static DependencyMappings INSTANCE = new DependencyMappings();
	
	private final List<DependencyMapping> mappings;
	
	private DependencyMappings()
	{
		this.mappings = this.loadMappings();
	}
	
	@Override
	public Iterator<DependencyMapping> iterator()
	{
		return this.mappings.iterator();
	}
	
	private List<DependencyMapping> loadMappings()
	{
		final List<DependencyMapping> mappings = new ArrayList<>();
		
		try(final BufferedReader reader = new BufferedReader(new InputStreamReader(
			this.getClass().getResourceAsStream("/META-INF/mappings/dependency.mappings")
		)))
		{
			String line;
			while((line = reader.readLine()) != null)
			{
				final String[] parts       = line.split("=");
				final String[] microstream = parts[0].split(":");
				final String[] eclipse     = parts[1].split(":");
				mappings.add(new DependencyMapping(
					microstream[0].trim(),
					microstream[1].trim(),
					eclipse[0].trim(),
					eclipse[1].trim()
				));
			}
		}
		catch(final IOException ioe)
		{
			throw new RuntimeException(ioe);
		}
		
		return mappings;
	}
	
}
