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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.eclipse.serializer.Serializer;
import org.eclipse.serializer.SerializerFoundation;
import org.junit.jupiter.api.Test;

import one.microstream.test.serializerstandalone.domain.Customer;
import one.microstream.test.serializerstandalone.domain.Order;

/**
 * Pitfall #2 — close() truncates the registry and closes the persistence manager.
 * Verify the try-with-resources contract: round-trip inside, no exception after close.
 */
class CloseTruncatesRegistryTest
{
	@Test
	void try_with_resources_round_trip_and_close()
	{
		assertDoesNotThrow(() ->
		{
			final SerializerFoundation<?> sf = SerializerFoundation.New()
				.registerEntityTypes(Customer.class, Order.class);

			try (Serializer<byte[]> ser = Serializer.Bytes(sf))
			{
				final Customer c = new Customer("close@acme.com");
				c.orders().add(new Order("ord-x"));

				final byte[]   bytes = ser.serialize(c);
				final Customer back  = ser.deserialize(bytes);

				assertEquals("close@acme.com", back.email());
				assertEquals("ord-x", back.orders().get(0).id());
			}
		});
	}
}
