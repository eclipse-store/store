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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import org.eclipse.serializer.Serializer;
import org.eclipse.serializer.TypedSerializer;
import org.junit.jupiter.api.Test;

import one.microstream.test.serializerstandalone.domain.Customer;
import one.microstream.test.serializerstandalone.domain.Order;

/**
 * Pattern C — TypedSerializer for open-world messaging.
 * Producer and consumer use fresh TypedSerializer.Bytes() instances with no
 * pre-registration: type info travels in-band.
 */
class TypedSerializerOpenWorldTest
{
	@Test
	void consumer_can_deserialize_without_pre_registration() throws Exception
	{
		final Customer original = new Customer("bob@acme.com");
		original.orders().add(new Order("ord-99"));

		final byte[] payload;
		try (Serializer<byte[]> producer = TypedSerializer.Bytes())
		{
			payload = producer.serialize(original);
		}

		try (Serializer<byte[]> consumer = TypedSerializer.Bytes())
		{
			final Object back = consumer.deserialize(payload);

			final Customer c = assertInstanceOf(Customer.class, back);
			assertEquals("bob@acme.com", c.email());
			assertEquals(1, c.orders().size());
			assertEquals("ord-99", c.orders().get(0).id());
		}
	}
}
