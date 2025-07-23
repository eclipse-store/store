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

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class AllIBaseTypeEntity
{
    @Index
    public String stringField;

    @Index
    public int intField;

    @Index
    public long longField;

    @Index
    public double doubleField;

    @Index
    public float floatField;

    @Index
    public boolean booleanField;

    @Index
    public char charField;

    @Index
    public byte byteField;

    @Index
    public short shortField;

    @Index
    public UUID uuidField;
    
    @Index
    public String[] arrayField;
    
    @Index
    public List<String> listField;

    public AllIBaseTypeEntity(String stringField, int intField, long longField, double doubleField, float floatField, boolean booleanField, char charField, byte byteField, short shortField, UUID uuidField, String[] arrayField)
    {
        this.stringField = stringField;
        this.intField = intField;
        this.longField = longField;
        this.doubleField = doubleField;
        this.floatField = floatField;
        this.booleanField = booleanField;
        this.charField = charField;
        this.byteField = byteField;
        this.shortField = shortField;
        this.uuidField = uuidField;
        this.arrayField = arrayField;
        this.listField = Arrays.asList(arrayField);
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

    public byte getByteField()
    {
        return byteField;
    }

    public short getShortField()
    {
        return shortField;
    }

    public UUID getUuidField()
    {
        return uuidField;
    }
    
    public String[] getArrayField()
	{
		return arrayField;
	}
    
    public List<String> getListField()
	{
		return listField;
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
                ", byteField=" + byteField +
                ", shortField=" + shortField +
                ", uuidField=" + uuidField +
                ", arrayField=" + Arrays.toString(arrayField) +
                ", listField=" + listField +
                '}';
    }
}
