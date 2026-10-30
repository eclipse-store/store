package one.microstream.test.customtypehandlers;

/*-
 * #%L
 * EclipseStore Integration Tests - Skills - custom-type-handlers
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
import org.eclipse.serializer.SerializerFoundation;
import org.junit.jupiter.api.Test;

class IpAddressHandlerRoundTripTest
{
    @Test
    void ip_address_roundtrip_via_standalone_serializer()
    {
        SerializerFoundation<?> sf = SerializerFoundation.New()
            .registerCustomTypeHandler(new IpAddressHandler());
        Serializer<byte[]> ser = Serializer.Bytes(sf);

        IpAddress original = new IpAddress(0xC0A80101);
        byte[]    bytes    = ser.serialize(original);
        IpAddress back     = ser.deserialize(bytes);

        assertEquals(original, back, "value-equal after round-trip");
        assertEquals(0xC0A80101, back.packed(), "packed int preserved");
        assertEquals("192.168.1.1", back.toDotted(), "dotted form preserved");
    }
}
