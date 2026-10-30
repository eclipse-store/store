package one.microstream.test.serializerstandalone;

/*-
 * #%L
 * EclipseStore Integration Tests - Skills - serializer-standalone
 * %%
 * Copyright (C) 2023 - 2026 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.eclipse.serializer.Serializer;
import org.junit.jupiter.api.Test;

/**
 * Pattern A — Quick start, default serializer.
 */
class DefaultBytesRoundTripTest
{
	@Test
	void primitive_string_roundtrips() throws Exception
	{
		try (Serializer<byte[]> ser = Serializer.Bytes())
		{
			final String original = "hello";
			final byte[] bytes    = ser.serialize(original);
			final String back     = ser.deserialize(bytes);

			assertEquals(original, back);
		}
	}
}
