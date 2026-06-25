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
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.eclipse.serializer.configuration.types.ByteSize;
import org.eclipse.serializer.configuration.types.ByteUnit;
import org.junit.jupiter.api.Test;


class ByteSizeTest {

    @Test
    void parser() {
        final ByteSize size = ByteSize.New(1.23, ByteUnit.MB);
        assertEquals(size, ByteSize.New(size.toString()));
    }
    
    @Test
    void equality() {
    	assertEquals(
    		ByteSize.New(0.5, ByteUnit.MB),
    		ByteSize.New(500, ByteUnit.KB)
    	);
    }
    
    @Test
    void error() {
    	assertThrows(
    		IllegalArgumentException.class,
    		() -> ByteSize.New("1xyz")
    	);
    }
}
