package test.eclipse.store.library.types;

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

import org.junit.jupiter.api.Assertions;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SocketAddressData implements BinaryHandlerTestData {

        SocketAddress socketAddress;

        @Override
        public SocketAddressData fillSampleData() {
            try {
                socketAddress = new InetSocketAddress(80);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            return this;
        }

    @Override
    public void proveResults(Object o) {
        Assertions.assertNotNull(o);
        SocketAddressData copy = (SocketAddressData)o;
        assertEquals(this.getSocketAddress(), copy.getSocketAddress(), "java.net.InetAddress");
    }

    public SocketAddress getSocketAddress() {
            return socketAddress;
        }
    }
