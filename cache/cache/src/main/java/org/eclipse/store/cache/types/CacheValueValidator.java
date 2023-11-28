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

public interface CacheValueValidator
{
	public void validate(
		Object value
	);
	
	
	public static CacheValueValidator New(
		final String slot,
		final Class<?> expectedType
	)
	{
		return expectedType == null || Object.class.equals(expectedType)
			? new Simple(slot)
			: new Typed(slot, expectedType)
		;
	}
	
	
	public static class Simple implements CacheValueValidator
	{
		final String slot;

		Simple(
			final String slot
		)
		{
			super();
			this.slot = slot;
		}
				
		@Override
		public void validate(
			final Object value
		)
		{
			if(value == null)
			{
				throw new NullPointerException(
					this.slot + " cannot be null"
				);
			}
		}
		
	}
	
	public static class Typed extends Simple
	{
		final Class<?> expectedType;

		Typed(
			final String slot,
			final Class<?> expectedType
		)
		{
			super(slot);
			this.expectedType = expectedType;
		}
		
		@Override
		public void validate(
			final Object value
		)
		{
			super.validate(value); // null check
			
			if(!this.expectedType.isInstance(value))
			{
				throw new ClassCastException(
					"Type mismatch for " + this.slot + ": " +
					value + " <> " + this.expectedType.getName()
				);
			}
		}
		
	}
	
}
