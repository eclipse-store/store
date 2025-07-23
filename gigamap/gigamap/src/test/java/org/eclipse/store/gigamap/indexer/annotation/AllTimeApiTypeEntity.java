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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;

public class AllTimeApiTypeEntity
{
    @Index
    public LocalDateTime localDateTimeField;

    @Index
    public LocalDate localDateField;

    @Index
    public LocalTime localTimeField;

    @Index
    public YearMonth yearMonthField;


    public AllTimeApiTypeEntity(LocalDateTime localDateTimeField, LocalDate localDateField, LocalTime localTimeField, YearMonth yearMonthField)
    {
        this.localDateTimeField = localDateTimeField;
        this.localDateField = localDateField;
        this.localTimeField = localTimeField;
        this.yearMonthField = yearMonthField;
    }

    public LocalDateTime getLocalDateTimeField()
    {
        return localDateTimeField;
    }

    public LocalDate getLocalDateField()
    {
        return localDateField;
    }

    public LocalTime getLocalTimeField()
    {
        return localTimeField;
    }

    public YearMonth getYearMonthField()
    {
        return yearMonthField;
    }

    @Override
    public String toString()
    {
        return "AllTimeApiTypeEntity{" +
                "localDateTimeField=" + localDateTimeField +
                ", localDateField=" + localDateField +
                ", localTimeField=" + localTimeField +
                ", yearMonthField=" + yearMonthField +
                '}';
    }
}
