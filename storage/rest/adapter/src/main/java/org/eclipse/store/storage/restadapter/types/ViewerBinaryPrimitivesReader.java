package org.eclipse.store.storage.restadapter.types;

/*-
 * #%L
 * EclipseStore Storage REST Adapter
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

import org.eclipse.serializer.persistence.binary.types.Binary;

import java.lang.reflect.Type;

public class ViewerBinaryPrimitivesReader
{
	public static Object readPrimitive(final Type type, final Binary bytes, final long offset )
	{
		if(type == char.class)
		{
			return bytes.read_char(offset);
		}

		if(type == boolean.class)
		{
			return bytes.read_boolean(offset);
		}

		if(type == byte.class)
		{
			return bytes.read_byte(offset);
		}

		if(type == short.class)
		{
			return bytes.read_short(offset);
		}

		if(type == int.class)
		{
			return bytes.read_int(offset);
		}

		if(type == long.class)
		{
			return bytes.read_long(offset);
		}

		if(type == float.class)
		{
			return bytes.read_float(offset);
		}

		if(type == double.class)
		{
			return bytes.read_double(offset);
		}

		return null;
	}

	public static long readReference(final Binary bytes, final long offset)
	{
		return bytes.read_long(offset);
	}
}
