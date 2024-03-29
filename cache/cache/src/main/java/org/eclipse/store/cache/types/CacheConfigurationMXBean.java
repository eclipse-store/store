
package org.eclipse.store.cache.types;

/*-
 * #%L
 * EclipseStore Cache
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

import javax.cache.configuration.CompleteConfiguration;


public interface CacheConfigurationMXBean extends javax.cache.management.CacheMXBean
{
	public static class Default implements CacheConfigurationMXBean
	{
		private final CompleteConfiguration<?, ?> configuration;
		
		Default(final CompleteConfiguration<?, ?> configuration)
		{
			super();
			
			this.configuration = configuration;
		}
		
		@Override
		public String getKeyType()
		{
			return this.configuration.getKeyType().getName();
		}
		
		@Override
		public String getValueType()
		{
			return this.configuration.getValueType().getName();
		}
		
		@Override
		public boolean isReadThrough()
		{
			return this.configuration.isReadThrough();
		}
		
		@Override
		public boolean isWriteThrough()
		{
			return this.configuration.isWriteThrough();
		}
		
		@Override
		public boolean isStoreByValue()
		{
			return this.configuration.isStoreByValue();
		}
		
		@Override
		public boolean isStatisticsEnabled()
		{
			return this.configuration.isStatisticsEnabled();
		}
		
		@Override
		public boolean isManagementEnabled()
		{
			return this.configuration.isManagementEnabled();
		}
		
	}
	
}
