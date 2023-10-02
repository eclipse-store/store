package org.eclipse.store.storage.embedded.tools.storage.migrator.mappings;

/*-
 * #%L
 * EclipseStore Storage Embedded Tools Storage Migrator
 * %%
 * Copyright (C) 2023 MicroStream Software
 * %%
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the Eclipse
 * Public License, v. 2.0 are satisfied: GNU General Public License, version 2
 * with the GNU Classpath Exception which is
 * available at https://www.gnu.org/software/classpath/license.html.
 *
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 * #L%
 */

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;


public class PackageMappings implements Iterable<Map.Entry<String, String>>
{
	public final static PackageMappings INSTANCE = new PackageMappings();
	
	private final Map<String, String> mappings;
	
	private PackageMappings()
	{
		this.mappings = this.loadMappings();
	}
	
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
