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

import org.eclipse.store.gigamap.annotations.Index;

public class AllTypeEntity
{
    @Index
    public String stringField;

    @Index
    public Integer intField;

    @Index
    public Long longField;

    @Index
    public Double doubleField;

    @Index
    public Float floatField;

    @Index
    public Boolean booleanField;

    @Index
    public Character charField;

    @Index
    public Short shortField;

    public AllTypeEntity(String stringField, Integer intField, Long longField, Double doubleField, Float floatField, Boolean booleanField, Character charField, Short shortField)
    {
        this.stringField = stringField;
        this.intField = intField;
        this.longField = longField;
        this.doubleField = doubleField;
        this.floatField = floatField;
        this.booleanField = booleanField;
        this.charField = charField;
        this.shortField = shortField;
    }

    public String getStringField()
    {
        return stringField;
    }

    public int getIntField()
    {
        return intField;
    }

    public long getLongField()
    {
        return longField;
    }

    public double getDoubleField()
    {
        return doubleField;
    }

    public float getFloatField()
    {
        return floatField;
    }

    public boolean isBooleanField()
    {
        return booleanField;
    }

    public char getCharField()
    {
        return charField;
    }

    public short getShortField()
    {
        return shortField;
    }

    @Override
    public String toString()
    {
        return "AllIBaseTypeEntity{" +
                "stringField='" + stringField + '\'' +
                ", intField=" + intField +
                ", longField=" + longField +
                ", doubleField=" + doubleField +
                ", floatField=" + floatField +
                ", booleanField=" + booleanField +
                ", charField=" + charField +
                ", shortField=" + shortField +
                '}';
    }
}
