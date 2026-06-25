package test.eclipse.store.legacy.legacy.enumeration.data;

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

public enum EnumerationOrig {
    FIRST("first", "10", "20"),
    SECOND("second", "second_value_30", "second_value_40" )

    ;

    private String name;
    private String value;
    private String secondValue;

    EnumerationOrig(String name, String value, String secondValue) {
        this.name = name;
        this.value = value;
        this.secondValue = secondValue;
    }

    public String getName() {
        return name;
    }

    public String getValue() {
        return value;
    }

    public String getSecondValue() {
        return secondValue;
    }
}
