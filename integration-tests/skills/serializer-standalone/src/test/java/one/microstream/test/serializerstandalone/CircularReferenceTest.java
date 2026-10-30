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
import static org.junit.jupiter.api.Assertions.assertSame;

import org.eclipse.serializer.Serializer;
import org.eclipse.serializer.SerializerFoundation;
import org.junit.jupiter.api.Test;

import one.microstream.test.serializerstandalone.domain.Customer;
import one.microstream.test.serializerstandalone.domain.Order;

/**
 * Pattern F — Circular references just work; reference identity is preserved.
 */
class CircularReferenceTest
{
	@Test
	void customer_order_cycle_preserves_identity() throws Exception
	{
		final Customer customer = new Customer("alice@acme.com");
		final Order    order    = new Order("ord-1");
		customer.orders().add(order);
		order.setCustomer(customer);

		final SerializerFoundation<?> sf = SerializerFoundation.New()
			.registerEntityTypes(Customer.class, Order.class);

		try (Serializer<byte[]> ser = Serializer.Bytes(sf))
		{
			final byte[]   bytes = ser.serialize(customer);
			final Customer back  = ser.deserialize(bytes);

			assertEquals("alice@acme.com", back.email());
			assertEquals(1, back.orders().size());
			assertSame(back, back.orders().get(0).customer());
		}
	}
}
