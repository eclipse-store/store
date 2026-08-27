package test.eclipse.store.configuration.convert;

/*-
 * #%L
 * EclipseStore Integration Tests
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

import org.eclipse.serializer.configuration.types.ByteUnit;
import org.junit.jupiter.api.Test;


class ByteUnitTest
{

    @Test
    void mbToGg()
    {
        final Double d = ByteUnit.convert(1.5, ByteUnit.MB).to(ByteUnit.GB);
        assertEquals(0.0015, d);
    }

    @Test
    void findUnitName()
    {
        final ByteUnit b = ByteUnit.ofName("kibibyte");
        //System.out.println(b.toString());
        assertEquals("KiB", b.toString());
    }
}
