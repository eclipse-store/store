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
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.eclipse.serializer.Serializer;
import org.eclipse.serializer.SerializerFoundation;
import org.junit.jupiter.api.Test;

import one.microstream.test.serializerstandalone.domain.Customer;
import one.microstream.test.serializerstandalone.domain.Order;

/**
 * Pattern B — Pre-register domain classes.
 */
class RegisteredDomainTypesTest
{
	@Test
	void customer_with_orders_roundtrips() throws Exception
	{
		final SerializerFoundation<?> sf = SerializerFoundation.New()
			.registerEntityTypes(Customer.class, Order.class);

		try (Serializer<byte[]> ser = Serializer.Bytes(sf))
		{
			final Customer original = new Customer("alice@acme.com");
			original.orders().add(new Order("ord-1"));
			original.orders().add(new Order("ord-2"));

			final byte[]   bytes = ser.serialize(original);
			final Customer back  = ser.deserialize(bytes);

			assertNotNull(back);
			assertEquals("alice@acme.com", back.email());
			assertEquals(2, back.orders().size());
			assertEquals("ord-1", back.orders().get(0).id());
			assertEquals("ord-2", back.orders().get(1).id());
		}
	}
}
