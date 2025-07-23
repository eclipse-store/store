package org.eclipse.store.gigamap.restart;

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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.UUID;

public class AllTypesPojo
{
    private String stringField;
    private int intField;
    private long longField;
    private double doubleField;
    private boolean booleanField;
    private char charField;
    private byte byteField;
    private short shortField;
    private float floatField;
    private Integer integerField;
    private Long longObjectField;
    private Double doubleObjectField;
    private Boolean booleanObjectField;
    private Character charObjectField;
    private Byte byteObjectField;
    private Short shortObjectField;
    private Float floatObjectField;
    private LocalDate localDateField;
    private LocalDateTime localDateTimeField;
    private LocalTime localTimeField;
    private UUID uuidField;
    private YearMonth yearMonthField;

    //add Method to generate random data
    public void generateRandomData()
    {
        this.stringField = UUID.randomUUID().toString();
        this.intField = (int) (Math.random() * 100);
        this.longField = (long) (Math.random() * 1000000);
        this.doubleField = Math.random() * 100;
        this.booleanField = Math.random() > 0.5;
        this.charField = (char) ('a' + Math.random() * 26);
        this.byteField = (byte) (Math.random() * 100);
        this.shortField = (short) (Math.random() * 100);
        this.floatField = (float) (Math.random() * 100);
        this.integerField = (int) (Math.random() * 100);
        this.longObjectField = (long) (Math.random() * 1000000);
        this.doubleObjectField = Math.random() * 100;
        this.booleanObjectField = Math.random() > 0.5;
        this.charObjectField = (char) ('a' + Math.random() * 26);
        this.byteObjectField = (byte) (Math.random() * 100);
        this.shortObjectField = (short) (Math.random() * 100);
        this.floatObjectField = (float) (Math.random() * 100);
        this.localDateField = LocalDate.now().plusDays((int) (Math.random() * 10));
        this.localDateTimeField = LocalDateTime.now().plusDays((int) (Math.random() * 10));
        this.localTimeField = LocalTime.now().plusHours((int) (Math.random() * 10));
        this.uuidField = UUID.randomUUID();
        this.yearMonthField = YearMonth.now().plusMonths((int) (Math.random() * 10));
    }

    public String getStringField()
    {
        return stringField;
    }

    public void setStringField(String stringField)
    {
        this.stringField = stringField;
    }

    public int getIntField()
    {
        return intField;
    }

    public void setIntField(int intField)
    {
        this.intField = intField;
    }

    public long getLongField()
    {
        return longField;
    }

    public void setLongField(long longField)
    {
        this.longField = longField;
    }

    public double getDoubleField()
    {
        return doubleField;
    }

    public void setDoubleField(double doubleField)
    {
        this.doubleField = doubleField;
    }

    public boolean isBooleanField()
    {
        return booleanField;
    }

    public void setBooleanField(boolean booleanField)
    {
        this.booleanField = booleanField;
    }

    public char getCharField()
    {
        return charField;
    }

    public void setCharField(char charField)
    {
        this.charField = charField;
    }

    public byte getByteField()
    {
        return byteField;
    }

    public void setByteField(byte byteField)
    {
        this.byteField = byteField;
    }

    public short getShortField()
    {
        return shortField;
    }

    public void setShortField(short shortField)
    {
        this.shortField = shortField;
    }

    public float getFloatField()
    {
        return floatField;
    }

    public void setFloatField(float floatField)
    {
        this.floatField = floatField;
    }

    public Integer getIntegerField()
    {
        return integerField;
    }

    public void setIntegerField(Integer integerField)
    {
        this.integerField = integerField;
    }

    public Long getLongObjectField()
    {
        return longObjectField;
    }

    public void setLongObjectField(Long longObjectField)
    {
        this.longObjectField = longObjectField;
    }

    public Double getDoubleObjectField()
    {
        return doubleObjectField;
    }

    public void setDoubleObjectField(Double doubleObjectField)
    {
        this.doubleObjectField = doubleObjectField;
    }

    public Boolean getBooleanObjectField()
    {
        return booleanObjectField;
    }

    public void setBooleanObjectField(Boolean booleanObjectField)
    {
        this.booleanObjectField = booleanObjectField;
    }

    public Character getCharObjectField()
    {
        return charObjectField;
    }

    public void setCharObjectField(Character charObjectField)
    {
        this.charObjectField = charObjectField;
    }

    public Byte getByteObjectField()
    {
        return byteObjectField;
    }

    public void setByteObjectField(Byte byteObjectField)
    {
        this.byteObjectField = byteObjectField;
    }

    public Short getShortObjectField()
    {
        return shortObjectField;
    }

    public void setShortObjectField(Short shortObjectField)
    {
        this.shortObjectField = shortObjectField;
    }

    public Float getFloatObjectField()
    {
        return floatObjectField;
    }

    public void setFloatObjectField(Float floatObjectField)
    {
        this.floatObjectField = floatObjectField;
    }

    public LocalDate getLocalDateField()
    {
        return localDateField;
    }

    public void setLocalDateField(LocalDate localDateField)
    {
        this.localDateField = localDateField;
    }

    public LocalDateTime getLocalDateTimeField()
    {
        return localDateTimeField;
    }

    public void setLocalDateTimeField(LocalDateTime localDateTimeField)
    {
        this.localDateTimeField = localDateTimeField;
    }

    public LocalTime getLocalTimeField()
    {
        return localTimeField;
    }

    public void setLocalTimeField(LocalTime localTimeField)
    {
        this.localTimeField = localTimeField;
    }

    public UUID getUuidField()
    {
        return uuidField;
    }

    public void setUuidField(UUID uuidField)
    {
        this.uuidField = uuidField;
    }

    public YearMonth getYearMonthField()
    {
        return yearMonthField;
    }

    public void setYearMonthField(YearMonth yearMonthField)
    {
        this.yearMonthField = yearMonthField;
    }

    @Override
    public String toString()
    {
        return "AllTypesPojo{" +
                "stringField='" + stringField + '\'' +
                ", intField=" + intField +
                ", longField=" + longField +
                ", doubleField=" + doubleField +
                ", booleanField=" + booleanField +
                ", charField=" + charField +
                ", byteField=" + byteField +
                ", shortField=" + shortField +
                ", floatField=" + floatField +
                ", integerField=" + integerField +
                ", longObjectField=" + longObjectField +
                ", doubleObjectField=" + doubleObjectField +
                ", booleanObjectField=" + booleanObjectField +
                ", charObjectField=" + charObjectField +
                ", byteObjectField=" + byteObjectField +
                ", shortObjectField=" + shortObjectField +
                ", floatObjectField=" + floatObjectField +
                ", localDateField=" + localDateField +
                ", localDateTimeField=" + localDateTimeField +
                ", localTimeField=" + localTimeField +
                ", uuidField=" + uuidField +
                ", yearMonthField=" + yearMonthField +
                '}';
    }
}
