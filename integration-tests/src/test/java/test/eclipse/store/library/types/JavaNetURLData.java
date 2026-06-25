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

import java.net.URL;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JavaNetURLData implements BinaryHandlerTestData {

    URL url;

    @Override
    public JavaNetURLData fillSampleData() {
        try {
            url = new URL("http://example.com/pages/");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return this;
    }

    public URL getUrl() {
        return url;
    }

    @Override
    public void proveResults(Object o) {
        Assertions.assertNotNull(o);
        JavaNetURLData copy = (JavaNetURLData) o;
        assertEquals(this.getUrl(), copy.getUrl(), "java.net.URL");
    }
}
