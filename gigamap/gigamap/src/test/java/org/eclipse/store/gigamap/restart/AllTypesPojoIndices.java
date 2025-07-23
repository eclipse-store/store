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

import org.eclipse.store.gigamap.types.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.UUID;


public class AllTypesPojoIndices
{
    public final static IndexerString<AllTypesPojo> stringFieldIndex = new IndexerString.Abstract<>()
    {
        public String name()
        {
            return "stringField";
        }

        @Override
        protected String getString(final AllTypesPojo entity)
        {
            return entity.getStringField();
        }
    };

    public final static IndexerInteger<AllTypesPojo> intFieldIndex = new IndexerInteger.Abstract<>()
    {

        public String name()
        {
            return "intField";
        }

        @Override
        protected Integer getInteger(AllTypesPojo entity)
        {
            return entity.getIntField();
        }
    };

    public final static IndexerLong<AllTypesPojo> longFieldIndex = new IndexerLong.Abstract<>()
    {
        @Override
        protected Long getLong(AllTypesPojo entity)
        {
            return entity.getLongField();
        }

        public String name()
        {
            return "longField";
        }

    };

    public final static IndexerDouble<AllTypesPojo> doubleFieldIndex = new IndexerDouble.Abstract<>()
    {
        @Override
        protected Double getDouble(AllTypesPojo entity)
        {
            return entity.getDoubleField();
        }

        public String name()
        {
            return "doubleField";
        }
    };

    public static final IndexerBoolean<AllTypesPojo> booleanFieldIndex = new IndexerBoolean.Abstract<>()
    {
        @Override
        protected Boolean getBoolean(AllTypesPojo entity)
        {
            return entity.isBooleanField();
        }

        public String name()
        {
            return "booleanField";
        }
    };

    public static final IndexerCharacter<AllTypesPojo> charFieldIndex = new IndexerCharacter.Abstract<>()
    {
        @Override
        protected Character getCharacter(AllTypesPojo entity)
        {
            return entity.getCharField();
        }

        public String name()
        {
            return "charField";
        }
    };

    public static final IndexerByte<AllTypesPojo> byteFieldIndex = new IndexerByte.Abstract<>()
    {
        @Override
        protected Byte getByte(AllTypesPojo entity)
        {
            return entity.getByteField();
        }

        public String name()
        {
            return "byteField";
        }
    };

    public static final IndexerShort<AllTypesPojo> shortFieldIndex = new IndexerShort.Abstract<>()
    {
        @Override
        protected Short getShort(AllTypesPojo entity)
        {
            return entity.getShortField();
        }

        public String name()
        {
            return "shortField";
        }
    };

    public static final IndexerFloat<AllTypesPojo> floatFieldIndex = new IndexerFloat.Abstract<>()
    {
        @Override
        protected Float getFloat(AllTypesPojo entity)
        {
            return entity.getFloatField();
        }

        public String name()
        {
            return "floatField";
        }
    };

    //===

    public final static IndexerLong<AllTypesPojo> longObjectFieldIndex = new IndexerLong.Abstract<>()
    {
        @Override
        protected Long getLong(AllTypesPojo entity)
        {
            return entity.getLongObjectField();
        }

        public String name()
        {
            return "longFieldObject";
        }

    };

    public final static IndexerDouble<AllTypesPojo> doubleFieldObjectIndex = new IndexerDouble.Abstract<>()
    {
        @Override
        protected Double getDouble(AllTypesPojo entity)
        {
            return entity.getDoubleObjectField();
        }

        public String name()
        {
            return "doubleFieldObject";
        }
    };

    public static final IndexerBoolean<AllTypesPojo> booleanFieldObjectIndex = new IndexerBoolean.Abstract<>()
    {
        @Override
        protected Boolean getBoolean(AllTypesPojo entity)
        {
            return entity.getBooleanObjectField();
        }

        public String name()
        {
            return "booleanFieldObject";
        }
    };

    public static final IndexerCharacter<AllTypesPojo> charFieldObjectIndex = new IndexerCharacter.Abstract<>()
    {
        @Override
        protected Character getCharacter(AllTypesPojo entity)
        {
            return entity.getCharObjectField();
        }

        public String name()
        {
            return "charFieldObject";
        }
    };

    public static final IndexerByte<AllTypesPojo> byteFieldObjectIndex = new IndexerByte.Abstract<>()
    {
        @Override
        protected Byte getByte(AllTypesPojo entity)
        {
            return entity.getByteObjectField();
        }

        public String name()
        {
            return "byteFieldObject";
        }
    };

    public static final IndexerShort<AllTypesPojo> shortFieldObjectIndex = new IndexerShort.Abstract<>()
    {
        @Override
        protected Short getShort(AllTypesPojo entity)
        {
            return entity.getShortObjectField();
        }

        public String name()
        {
            return "shortFieldObject";
        }
    };

    public static final IndexerFloat<AllTypesPojo> floatFieldObjectIndex = new IndexerFloat.Abstract<>()
    {
        @Override
        protected Float getFloat(AllTypesPojo entity)
        {
            return entity.getFloatObjectField();
        }

        public String name()
        {
            return "floatFieldObject";
        }
    };

    //==

    public static final IndexerInteger<AllTypesPojo> bigIntegerFieldIndex = new IndexerInteger.Abstract<>()
    {
        @Override
        protected Integer getInteger(AllTypesPojo entity)
        {
            return entity.getIntegerField();
        }

        public String name()
        {
            return "integerObjectField";
        }
    };





    public static final IndexerLocalDate<AllTypesPojo> localDateFieldIndex = new IndexerLocalDate.Abstract<>()
    {
        @Override
        protected LocalDate getLocalDate(AllTypesPojo entity)
        {
            return entity.getLocalDateField();
        }

        public String name()
        {
            return "localDateField";
        }
    };

    public static final IndexerLocalDateTime<AllTypesPojo> localDateTimeFieldIndex = new IndexerLocalDateTime.Abstract<>()
    {
        @Override
        protected LocalDateTime getLocalDateTime(AllTypesPojo entity)
        {
            return entity.getLocalDateTimeField();
        }

        public String name()
        {
            return "localDateTimeField";
        }
    };

    public static final IndexerLocalTime<AllTypesPojo> localTimeFieldIndex = new IndexerLocalTime.Abstract<>()
    {
        @Override
        protected LocalTime getLocalTime(AllTypesPojo entity)
        {
            return entity.getLocalTimeField();
        }

        public String name()
        {
            return "localTimeField";
        }
    };

    public static final BinaryIndexerUUID<AllTypesPojo> uuidFieldIndex = new BinaryIndexerUUID.Abstract<>()
    {
        @Override
        protected UUID getUUID(AllTypesPojo entity)
        {
            return entity.getUuidField();
        }

        public String name()
        {
            return "uuidField";
        }
    };

    public static final IndexerYearMonth<AllTypesPojo> yearMonthFieldIndex = new IndexerYearMonth.Abstract<>()
    {
        @Override
        protected YearMonth getYearMonth(AllTypesPojo entity)
        {
            return entity.getYearMonthField();
        }

        public String name()
        {
            return "yearMonthField";
        }
    };
}
