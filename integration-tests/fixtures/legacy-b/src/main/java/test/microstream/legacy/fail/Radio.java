package test.microstream.legacy.fail;

/*-
 * #%L
 * EclipseStore Integration Tests - Fixtures - Legacy B
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

public class Radio {

    String producer;

    public Radio(String producer) {
        this.producer = producer;
    }

    @Override
    public String toString() {
        return "Radio{" +
                "producer='" + producer + '\'' +
                '}';
    }
}
