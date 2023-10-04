package org.eclipse.store.afs.oracle.coherence.types;

/*-
 * #%L
 * EclipseStore Abstract File System Oracle Coherence
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

import java.util.Map;

import com.tangosol.net.CacheFactory;
import com.tangosol.net.NamedCache;

import org.eclipse.store.afs.blobstore.types.BlobStoreFileSystem;
import org.eclipse.store.configuration.exceptions.ConfigurationException;
import org.eclipse.store.configuration.types.Configuration;
import org.eclipse.store.configuration.types.ConfigurationBasedCreator;
import org.eclipse.serializer.afs.types.AFileSystem;
import org.eclipse.serializer.chars.XChars;


public class OracleCoherenceFileSystemCreator extends ConfigurationBasedCreator.Abstract<AFileSystem>
{
	public OracleCoherenceFileSystemCreator()
	{
		super(AFileSystem.class);
	}
	
	@Override
	public AFileSystem create(
		final Configuration configuration
	)
	{
		final Configuration coherenceConfiguration = configuration.child("oracle.coherence");
		if(coherenceConfiguration == null)
		{
			return null;
		}
		
		final String cacheName = coherenceConfiguration.get("cache-name");
		if(XChars.isEmpty(cacheName))
		{
			throw new ConfigurationException(coherenceConfiguration, "Coherence cache-name must be defined");
		}
		
		coherenceConfiguration.opt("cache-config").ifPresent(
			value -> System.setProperty("tangosol.coherence.cacheconfig", value)
		);
		
		final NamedCache<String, Map<String, Object>> namedCache = CacheFactory.getCache(cacheName);
		final boolean                                 useCache   = configuration.optBoolean("cache").orElse(true);
		final OracleCoherenceConnector connector  = useCache
			? OracleCoherenceConnector.Caching(namedCache)
			: OracleCoherenceConnector.New(namedCache)
		;
		return BlobStoreFileSystem.New(connector);
	}
	
}
