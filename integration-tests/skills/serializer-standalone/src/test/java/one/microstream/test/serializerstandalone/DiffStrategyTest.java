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
import org.eclipse.serializer.SerializerFoundation;
import org.eclipse.serializer.SerializerTypeInfoStrategyCreator;
import org.eclipse.serializer.TypedSerializer;
import org.junit.jupiter.api.Test;

import one.microstream.test.serializerstandalone.domain.Customer;
import one.microstream.test.serializerstandalone.domain.Order;

/**
 * Pattern D — Tune type-info inclusion strategy: Diff(true) on a TypedSerializer
 * with the domain pre-registered.
 */
class DiffStrategyTest
{
	@Test
	void diff_strategy_roundtrips_multiple_objects() throws Exception
	{
		final SerializerFoundation<?> sf = SerializerFoundation.New()
			.registerEntityTypes(Customer.class, Order.class)
			.setSerializerTypeInfoStrategyCreator(
				new SerializerTypeInfoStrategyCreator.Diff(true)
			);

		try (Serializer<byte[]> ser = TypedSerializer.Bytes(sf))
		{
			final Customer c1 = new Customer("alice@acme.com");
			c1.orders().add(new Order("ord-a"));
			final Customer c2 = new Customer("bob@acme.com");
			final Order    o3 = new Order("ord-c");

			final byte[] b1 = ser.serialize(c1);
			final byte[] b2 = ser.serialize(c2);
			final byte[] b3 = ser.serialize(o3);

			final Customer back1 = assertInstanceOf(Customer.class, ser.deserialize(b1));
			final Customer back2 = assertInstanceOf(Customer.class, ser.deserialize(b2));
			final Order    back3 = assertInstanceOf(Order.class,    ser.deserialize(b3));

			assertEquals("alice@acme.com", back1.email());
			assertEquals(1, back1.orders().size());
			assertEquals("ord-a", back1.orders().get(0).id());
			assertEquals("bob@acme.com", back2.email());
			assertEquals("ord-c", back3.id());
		}
	}
}
