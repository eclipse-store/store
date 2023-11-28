
package org.eclipse.store.cache.types;

import org.eclipse.serializer.Serializer;
import org.eclipse.serializer.persistence.binary.types.Binary;

/*-
 * #%L
 * microstream-cache
 * %%
 * Copyright (C) 2019 - 2022 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

public interface ObjectConverter
{
	public <T> Object internalize(T value);
	
	public <T> T externalize(Object internal);
	
	
	public static ObjectConverter ByReference()
	{
		return new ByReference();
	}
	
	public static ObjectConverter ByValue(final Serializer<Binary> serializer)
	{
		return new ByValue(serializer);
	}
	
	
	public static class ByReference implements ObjectConverter
	{
		ByReference()
		{
			super();
		}
		
		@Override
		public <T> Object internalize(final T value)
		{
			return value;
		}
		
		@SuppressWarnings("unchecked")
		@Override
		public <T> T externalize(final Object internal)
		{
			return (T)internal;
		}
		
	}
	
	public static class ByValue implements ObjectConverter
	{
		private final Serializer<Binary> serializer;
		
		ByValue(final Serializer<Binary> serializer)
		{
			super();
			
			this.serializer = serializer;
		}
		
		@Override
		public <T> Object internalize(final T value)
		{
			return SerializedObject.New(
				value.hashCode(),
				this.serializer.serialize(value)
			);
		}
		
		@Override
		public <T> T externalize(final Object internal)
		{
			return this.serializer.deserialize(
				((SerializedObject)internal).serializedData()
			);
		}
		
	}
	
}
