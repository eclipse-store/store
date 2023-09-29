package org.eclipse.store.storage.embedded.tools.storage.migrator.typedictionary;

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

import java.util.function.Function;

import org.eclipse.serializer.persistence.types.PersistenceTypeNameMapper;
import org.eclipse.store.storage.embedded.tools.storage.migrator.mappings.PackageMappings;


public class RewriteTypeNameMapper implements PersistenceTypeNameMapper
{
	private final PersistenceTypeNameMapper defaultMapper;
	
	public RewriteTypeNameMapper(final PersistenceTypeNameMapper defaultMapper)
	{
		super();
		this.defaultMapper = defaultMapper;
	}
	
	@Override
	public String mapClassName(final String oldClassName)
	{
		return this.mapTypeName(oldClassName, this.defaultMapper::mapClassName);
	}
	
	@Override
	public String mapInterfaceName(final String oldInterfaceName)
	{
		return this.mapTypeName(oldInterfaceName, this.defaultMapper::mapInterfaceName);
	}
	
	private String mapTypeName(final String oldTypeName, final Function<String, String> defaultMapping)
	{
		final int lastDot = oldTypeName.lastIndexOf('.');
		if(lastDot > 0)
		{
			final String packageName = oldTypeName.substring(0, lastDot);
			final String newPackage  = PackageMappings.INSTANCE.newPackage(packageName);
			if(newPackage != null)
			{
				return newPackage.concat(oldTypeName.substring(lastDot));
			}
		}
		
		return defaultMapping.apply(oldTypeName);
	}
}
