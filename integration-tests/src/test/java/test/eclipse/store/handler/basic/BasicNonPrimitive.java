package test.eclipse.store.handler.basic;

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

import test.eclipse.store.handler.BinaryHandlerTestData;

public class BasicNonPrimitive implements BinaryHandlerTestData
{
    private static final Byte SAMPLE_BYTE = 100;
    private static final Short SAMPLE_SHORT = 50;
    private static final Integer SAMPLE_INT = 5401;
    private static final Long SAMPLE_LONG = 1545455464654L;
    private static final Float SAMPLE_FLOAT = 3.141526f;
    private static final Double SAMPLE_DOUBLE = 3.141526545;
    private static final Boolean SAMPLE_BOOLEAN = Boolean.TRUE;
    private static final Character SAMPLE_CHARACTER = 'c';
    private static final String SAMPLE_STRING = "sample string value \t \n";

    private Byte byteValue;
    private Short shortValue;
    private Integer intValue;
    private Long longValue;
    private Float floatValue;
    private Double doubleValue;
    private Boolean booleanValue;
    private Character characterValue;
    private String stringValue;

    @Override
    public void fillSampleData()
    {
        byteValue = SAMPLE_BYTE;
        shortValue = SAMPLE_SHORT;
        intValue = SAMPLE_INT;
        longValue = SAMPLE_LONG;
        floatValue = SAMPLE_FLOAT;
        doubleValue = SAMPLE_DOUBLE;
        booleanValue = SAMPLE_BOOLEAN;
        characterValue = SAMPLE_CHARACTER;
        stringValue = SAMPLE_STRING;
    }

    public Byte getByteValue()
    {
        return byteValue;
    }

    public Short getShortValue()
    {
        return shortValue;
    }

    public Integer getIntValue()
    {
        return intValue;
    }

    public Long getLongValue()
    {
        return longValue;
    }

    public Float getFloatValue()
    {
        return floatValue;
    }

    public Double getDoubleValue()
    {
        return doubleValue;
    }

    public Boolean getBooleanValue()
    {
        return booleanValue;
    }

    public Character getCharacterValue()
    {
        return characterValue;
    }

    public String getStringValue()
    {
        return stringValue;
    }

}
