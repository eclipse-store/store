
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

public interface Unwrappable
{
	public <T> T unwrap(final Class<T> clazz);
	
	final class Static
	{
		public static <T> T unwrap(final Object subject, final Class<T> clazz)
		{
			if(clazz.isAssignableFrom(subject.getClass()))
			{
				return clazz.cast(subject);
			}
			throw new IllegalArgumentException("Unwrapping to " + clazz + " is not supported by this implementation");
		}
		
		private Static()
		{
			throw new Error();
		}
		
	}
	
}
