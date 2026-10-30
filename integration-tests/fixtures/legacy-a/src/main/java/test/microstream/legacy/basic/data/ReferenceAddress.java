package test.microstream.legacy.basic.data;

/*-
 * #%L
 * EclipseStore Integration Tests - Fixtures - Legacy A
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

public class ReferenceAddress {

    private String street;

    public ReferenceAddress(String street) {
        this.street = street;
    }

    public ReferenceAddress() {
    }

    public static ReferenceAddress createSample() {
        return new ReferenceAddress("Fleischgasse 18");
    }

    public static ReferenceAddress createEmpty() {
        return new ReferenceAddress();
    }


    public String getStreet() {
        return street;
    }

    public void setStreet(String street) {
        this.street = street;
    }

    @Override
    public String toString() {
        return "ReferenceAddress{" +
                "street='" + street + '\'' +
                '}';
    }
}
