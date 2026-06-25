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

import java.net.InetAddress;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class IntetAddressData implements BinaryHandlerTestData {

    InetAddress inetAddress;

    @Override
    public IntetAddressData fillSampleData() {
        try {
            inetAddress = InetAddress.getByName("127.0.0.1");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return this;
    }

    public InetAddress getInetAddress() {
        return inetAddress;
    }

    @Override
    public void proveResults(Object o) {
        Assertions.assertNotNull(o);
        IntetAddressData copy = (IntetAddressData) o;
        assertEquals(this.getInetAddress(), copy.getInetAddress(), "java.net.InetAddress");
    }
}
