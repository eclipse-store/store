package org.eclipse.store.gigamap.indexer.annotation;

/*-
 * #%L
 * EclipseStore GigaMap
 * %%
 * Copyright (C) 2023 - 2025 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

public class AllTypeEntityBuilder
{
    private String stringField;
    private Integer intField;
    private Long longField;
    private Double doubleField;
    private Float floatField;
    private Boolean booleanField;
    private Character charField;
    private Short shortField;

    public AllTypeEntityBuilder setStringField(String stringField)
    {
        this.stringField = stringField;
        return this;
    }

    public AllTypeEntityBuilder setIntField(Integer intField)
    {
        this.intField = intField;
        return this;
    }

    public AllTypeEntityBuilder setLongField(Long longField)
    {
        this.longField = longField;
        return this;
    }

    public AllTypeEntityBuilder setDoubleField(Double doubleField)
    {
        this.doubleField = doubleField;
        return this;
    }

    public AllTypeEntityBuilder setFloatField(Float floatField)
    {
        this.floatField = floatField;
        return this;
    }

    public AllTypeEntityBuilder setBooleanField(Boolean booleanField)
    {
        this.booleanField = booleanField;
        return this;
    }

    public AllTypeEntityBuilder setCharField(Character charField)
    {
        this.charField = charField;
        return this;
    }

    public AllTypeEntityBuilder setShortField(Short shortField)
    {
        this.shortField = shortField;
        return this;
    }

    public AllTypeEntity createAllTypeEntity()
    {
        return new AllTypeEntity(stringField, intField, longField, doubleField, floatField, booleanField, charField, shortField);
    }
}
