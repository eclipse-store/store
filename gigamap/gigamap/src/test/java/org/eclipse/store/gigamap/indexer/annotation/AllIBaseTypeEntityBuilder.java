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

import java.util.UUID;

public class AllIBaseTypeEntityBuilder
{
    private String stringField;
    private int intField;
    private long longField;
    private double doubleField;
    private float floatField;
    private boolean booleanField;
    private char charField;
    private byte byteField;
    private short shortField;
    private UUID uuidField;
    private String[] arrayField;

    public AllIBaseTypeEntityBuilder setStringField(String stringField)
    {
        this.stringField = stringField;
        return this;
    }

    public AllIBaseTypeEntityBuilder setIntField(int intField)
    {
        this.intField = intField;
        return this;
    }

    public AllIBaseTypeEntityBuilder setLongField(long longField)
    {
        this.longField = longField;
        return this;
    }

    public AllIBaseTypeEntityBuilder setDoubleField(double doubleField)
    {
        this.doubleField = doubleField;
        return this;
    }

    public AllIBaseTypeEntityBuilder setFloatField(float floatField)
    {
        this.floatField = floatField;
        return this;
    }

    public AllIBaseTypeEntityBuilder setBooleanField(boolean booleanField)
    {
        this.booleanField = booleanField;
        return this;
    }

    public AllIBaseTypeEntityBuilder setCharField(char charField)
    {
        this.charField = charField;
        return this;
    }

    public AllIBaseTypeEntityBuilder setByteField(byte byteField)
    {
        this.byteField = byteField;
        return this;
    }

    public AllIBaseTypeEntityBuilder setShortField(short shortField)
    {
        this.shortField = shortField;
        return this;
    }

    public AllIBaseTypeEntityBuilder setUuidField(UUID uuidField)
    {
        this.uuidField = uuidField;
        return this;
    }
    
    public AllIBaseTypeEntityBuilder setArrayField(String... arrayField)
	{
		this.arrayField = arrayField;
        return this;
	}

    public AllIBaseTypeEntity build()
    {
        return new AllIBaseTypeEntity(stringField, intField, longField, doubleField, floatField, booleanField, charField, byteField, shortField, uuidField, arrayField);
    }
}
