
package org.eclipse.store.storage.restclient.types;

/*-
 * #%L
 * EclipseStore Storage REST Client
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

public interface StorageViewConfiguration extends ValueRenderer.Provider
{
	public long elementRangeMaximumLength();
	
	public long maxValueLength();
	
	
	public static StorageViewConfiguration Default()
	{
		return new StorageViewConfiguration.Default(
			100,
			10_000,
			ValueRenderer.DefaultProvider()
		);
	}
	
	public static StorageViewConfiguration New(
		final long elementRangeMaximumLength,
		final long maxValueLength,
		final ValueRenderer.Provider valueRendererProvider
	)
	{
		return new StorageViewConfiguration.Default(
			elementRangeMaximumLength,
			maxValueLength,
			valueRendererProvider
		);
	}
	
	
	public static class Default implements StorageViewConfiguration
	{
		private final long                   elementRangeMaximumLength;
		private final long                   maxValueLength;
		private final ValueRenderer.Provider valueRendererProvider;
		
		Default(
			final long elementRangeMaximumLength,
			final long maxValueLength,
			final ValueRenderer.Provider valueRendererProvider
		)
		{
			super();
			this.elementRangeMaximumLength = elementRangeMaximumLength;
			this.maxValueLength            = maxValueLength;
			this.valueRendererProvider     = valueRendererProvider;
		}
		
		@Override
		public long elementRangeMaximumLength()
		{
			return this.elementRangeMaximumLength;
		}

		@Override
		public long maxValueLength()
		{
			return this.maxValueLength;
		}
		
		@Override
		public ValueRenderer provideValueRenderer(
			final String typeName
		)
		{
			return this.valueRendererProvider.provideValueRenderer(typeName);
		}
		
	}
	
}
