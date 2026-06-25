package test.eclipse.store.library.types;

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

import static org.junit.jupiter.api.Assertions.*;

public class BasicNonPrimitive implements BinaryHandlerTestData {
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

    // corner-case fields
    private Byte byteValueNull;
    private Short shortValueNull;
    private Integer intValueMax;
    private Integer intValueMin;
    private Long longValueMax;
    private Long longValueMin;
    private Float floatNaN;
    private Float floatInfinite;
    private Double doubleNaN;
    private Double doubleInfinite;
    private String emptyString;
    private String whitespaceString;
    private String unicodeString;
    private String longString;
    private Boolean booleanNull;
    private Character characterNull;

    // ===== proposed edge-cases (review & cherry-pick) =====
    private Byte byteBoxedMin;
    private Byte byteBoxedMax;
    private Short shortBoxedMin;
    private Short shortBoxedMax;
    private Float floatNegativeInfinity;
    private Float floatBoxedMin;
    private Float floatBoxedMax;
    private Float floatBoxedMinNormal;
    private Float floatBoxedNegZero;
    private Float floatBoxedPosZero;
    private Double doublePositiveInfinity;
    private Double doubleBoxedMin;
    private Double doubleBoxedMax;
    private Double doubleBoxedMinNormal;
    private Double doubleBoxedNegZero;
    private Double doubleBoxedPosZero;
    private Character characterNul;
    private Character characterMaxBmp;
    private String stringWithNul;
    private String stringWith4ByteUtf8;

    @Override
    public BasicNonPrimitive fillSampleData() {
        byteValue = SAMPLE_BYTE;
        shortValue = SAMPLE_SHORT;
        intValue = SAMPLE_INT;
        longValue = SAMPLE_LONG;
        floatValue = SAMPLE_FLOAT;
        doubleValue = SAMPLE_DOUBLE;
        booleanValue = SAMPLE_BOOLEAN;
        characterValue = SAMPLE_CHARACTER;
        stringValue = SAMPLE_STRING;

        // corner-case initializations
        byteValueNull = null;
        shortValueNull = null;
        intValueMax = Integer.MAX_VALUE;
        intValueMin = Integer.MIN_VALUE;
        longValueMax = Long.MAX_VALUE;
        longValueMin = Long.MIN_VALUE;
        floatNaN = Float.NaN;
        floatInfinite = Float.POSITIVE_INFINITY;
        doubleNaN = Double.NaN;
        doubleInfinite = Double.NEGATIVE_INFINITY;
        emptyString = "";
        whitespaceString = "   \t\n";
        unicodeString = "čřžťýáíé 🙂 中文";
        StringBuilder sb = new StringBuilder(8000);
        for (int i = 0; i < 8000; i++) sb.append('a');
        longString = sb.toString();
        booleanNull = null;
        characterNull = null;

        // ===== proposed edge-cases =====
        byteBoxedMin = Byte.MIN_VALUE;
        byteBoxedMax = Byte.MAX_VALUE;
        shortBoxedMin = Short.MIN_VALUE;
        shortBoxedMax = Short.MAX_VALUE;
        floatNegativeInfinity = Float.NEGATIVE_INFINITY;
        floatBoxedMin = Float.MIN_VALUE;
        floatBoxedMax = Float.MAX_VALUE;
        floatBoxedMinNormal = Float.MIN_NORMAL;
        floatBoxedNegZero = -0.0f;
        floatBoxedPosZero = 0.0f;
        doublePositiveInfinity = Double.POSITIVE_INFINITY;
        doubleBoxedMin = Double.MIN_VALUE;
        doubleBoxedMax = Double.MAX_VALUE;
        doubleBoxedMinNormal = Double.MIN_NORMAL;
        doubleBoxedNegZero = -0.0;
        doubleBoxedPosZero = 0.0;
        characterNul = (char) 0;
        characterMaxBmp = (char) 0xFFFF;
        stringWithNul = "a" + ((char) 0) + "b";
        stringWith4ByteUtf8 = new String(Character.toChars(0x1F600));

        return this;
    }

    @Override
    public BinaryHandlerTestData updateSampleData() {
        byteValue = 50;
        shortValue = 50;
        intValue = 55;
        longValue = SAMPLE_LONG-10;
        floatValue = SAMPLE_FLOAT-10;
        doubleValue = SAMPLE_DOUBLE-10;
        booleanValue = Boolean.FALSE;
        characterValue = 'x';
        stringValue = SAMPLE_STRING + SAMPLE_STRING;

        // corner-case updates
        byteValueNull = (byte)-1;
        shortValueNull = (short)-2;
        if (intValueMax != null) intValueMax = intValueMax - 1;
        if (intValueMin != null) intValueMin = intValueMin + 1;
        if (longValueMax != null) longValueMax = longValueMax - 1;
        if (longValueMin != null) longValueMin = longValueMin + 1;
        floatInfinite = Float.NEGATIVE_INFINITY;
        doubleInfinite = Double.POSITIVE_INFINITY;
        emptyString = "now non empty";
        whitespaceString = "  trimmed  ";
        unicodeString = unicodeString + "追加";
        longString = longString + "-updated";
        booleanNull = Boolean.FALSE;
        characterNull = 'z';

        return this;
    }

    public Byte getByteValue() {
        return byteValue;
    }

    public Short getShortValue() {
        return shortValue;
    }

    public Integer getIntValue() {
        return intValue;
    }

    public Long getLongValue() {
        return longValue;
    }

    public Float getFloatValue() {
        return floatValue;
    }

    public Double getDoubleValue() {
        return doubleValue;
    }

    public Boolean getBooleanValue() {
        return booleanValue;
    }

    public Character getCharacterValue() {
        return characterValue;
    }

    public String getStringValue() {
        return stringValue;
    }

    // getters for corner-case fields
    public Byte getByteValueNull() { return byteValueNull; }
    public Short getShortValueNull() { return shortValueNull; }
    public Integer getIntValueMax() { return intValueMax; }
    public Integer getIntValueMin() { return intValueMin; }
    public Long getLongValueMax() { return longValueMax; }
    public Long getLongValueMin() { return longValueMin; }
    public Float getFloatNaN() { return floatNaN; }
    public Float getFloatInfinite() { return floatInfinite; }
    public Double getDoubleNaN() { return doubleNaN; }
    public Double getDoubleInfinite() { return doubleInfinite; }
    public String getEmptyString() { return emptyString; }
    public String getWhitespaceString() { return whitespaceString; }
    public String getUnicodeString() { return unicodeString; }
    public String getLongString() { return longString; }
    public Boolean getBooleanNull() { return booleanNull; }
    public Character getCharacterNull() { return characterNull; }

    // ===== proposed edge-cases — getters =====
    public Byte getByteBoxedMin() { return byteBoxedMin; }
    public Byte getByteBoxedMax() { return byteBoxedMax; }
    public Short getShortBoxedMin() { return shortBoxedMin; }
    public Short getShortBoxedMax() { return shortBoxedMax; }
    public Float getFloatNegativeInfinity() { return floatNegativeInfinity; }
    public Float getFloatBoxedMin() { return floatBoxedMin; }
    public Float getFloatBoxedMax() { return floatBoxedMax; }
    public Float getFloatBoxedMinNormal() { return floatBoxedMinNormal; }
    public Float getFloatBoxedNegZero() { return floatBoxedNegZero; }
    public Float getFloatBoxedPosZero() { return floatBoxedPosZero; }
    public Double getDoublePositiveInfinity() { return doublePositiveInfinity; }
    public Double getDoubleBoxedMin() { return doubleBoxedMin; }
    public Double getDoubleBoxedMax() { return doubleBoxedMax; }
    public Double getDoubleBoxedMinNormal() { return doubleBoxedMinNormal; }
    public Double getDoubleBoxedNegZero() { return doubleBoxedNegZero; }
    public Double getDoubleBoxedPosZero() { return doubleBoxedPosZero; }
    public Character getCharacterNul() { return characterNul; }
    public Character getCharacterMaxBmp() { return characterMaxBmp; }
    public String getStringWithNul() { return stringWithNul; }
    public String getStringWith4ByteUtf8() { return stringWith4ByteUtf8; }

    @Override
    public void proveResults(Object o) {
        assertNotNull(o);
        BasicNonPrimitive copy = (BasicNonPrimitive)o;
        assertAll("Basic non primitive Types tests",
                () -> assertEquals(this.getByteValue(), copy.getByteValue(), "Byte"),
                () -> assertEquals(this.getShortValue(), copy.getShortValue(), "Short"),
                () -> assertEquals(this.getIntValue(), copy.getIntValue(), "Integer"),
                () -> assertEquals(this.getLongValue(), copy.getLongValue(), "Long"),
                () -> assertEquals(this.getFloatValue(), copy.getFloatValue(), "Float"),
                () -> assertEquals(this.getDoubleValue(), copy.getDoubleValue(), "Double"),
                () -> assertEquals(this.getBooleanValue(), copy.getBooleanValue(), "Boolean"),
                () -> assertEquals(this.getCharacterValue(), copy.getCharacterValue(), "Character"),
                () -> assertEquals(this.getStringValue(), copy.getStringValue(), "String"),

                // corner-case verifications
                () -> assertEquals(this.getByteValueNull(), copy.getByteValueNull(), "Byte null"),
                () -> assertEquals(this.getShortValueNull(), copy.getShortValueNull(), "Short null"),
                () -> assertEquals(this.getIntValueMax(), copy.getIntValueMax(), "Integer max"),
                () -> assertEquals(this.getIntValueMin(), copy.getIntValueMin(), "Integer min"),
                () -> assertEquals(this.getLongValueMax(), copy.getLongValueMax(), "Long max"),
                () -> assertEquals(this.getLongValueMin(), copy.getLongValueMin(), "Long min"),
                () -> {
                    boolean nanThis = this.getFloatNaN() != null && Float.isNaN(this.getFloatNaN());
                    boolean nanCopy = copy.getFloatNaN() != null && Float.isNaN(copy.getFloatNaN());
                    assertEquals(nanThis, nanCopy, "Float NaN parity");
                },
                () -> assertEquals(this.getFloatInfinite(), copy.getFloatInfinite(), "Float infinite"),
                () -> {
                    boolean nanThisD = this.getDoubleNaN() != null && Double.isNaN(this.getDoubleNaN());
                    boolean nanCopyD = copy.getDoubleNaN() != null && Double.isNaN(copy.getDoubleNaN());
                    assertEquals(nanThisD, nanCopyD, "Double NaN parity");
                },
                () -> assertEquals(this.getDoubleInfinite(), copy.getDoubleInfinite(), "Double infinite"),
                () -> assertEquals(this.getEmptyString(), copy.getEmptyString(), "empty string"),
                () -> assertEquals(this.getWhitespaceString(), copy.getWhitespaceString(), "whitespace string"),
                () -> assertEquals(this.getUnicodeString(), copy.getUnicodeString(), "unicode string"),
                () -> assertEquals(this.getLongString(), copy.getLongString(), "long string"),
                () -> assertEquals(this.getBooleanNull(), copy.getBooleanNull(), "boolean null"),
                () -> assertEquals(this.getCharacterNull(), copy.getCharacterNull(), "character null"),

                // ===== proposed edge-case verifications =====
                () -> assertEquals(this.getByteBoxedMin(), copy.getByteBoxedMin(), "Byte MIN_VALUE"),
                () -> assertEquals(this.getByteBoxedMax(), copy.getByteBoxedMax(), "Byte MAX_VALUE"),
                () -> assertEquals(this.getShortBoxedMin(), copy.getShortBoxedMin(), "Short MIN_VALUE"),
                () -> assertEquals(this.getShortBoxedMax(), copy.getShortBoxedMax(), "Short MAX_VALUE"),
                // Float.equals uses floatToIntBits — robust for NaN, ±Infinity, signed zero, and null/null
                () -> assertEquals(this.getFloatNegativeInfinity(), copy.getFloatNegativeInfinity(), "Float -Infinity"),
                () -> assertEquals(this.getFloatBoxedMin(), copy.getFloatBoxedMin(), "Float MIN_VALUE"),
                () -> assertEquals(this.getFloatBoxedMax(), copy.getFloatBoxedMax(), "Float MAX_VALUE"),
                () -> assertEquals(this.getFloatBoxedMinNormal(), copy.getFloatBoxedMinNormal(), "Float MIN_NORMAL"),
                () -> assertEquals(this.getFloatBoxedNegZero(), copy.getFloatBoxedNegZero(), "Float -0.0f"),
                () -> assertEquals(this.getFloatBoxedPosZero(), copy.getFloatBoxedPosZero(), "Float +0.0f"),
                () -> assertEquals(this.getDoublePositiveInfinity(), copy.getDoublePositiveInfinity(), "Double +Infinity"),
                () -> assertEquals(this.getDoubleBoxedMin(), copy.getDoubleBoxedMin(), "Double MIN_VALUE"),
                () -> assertEquals(this.getDoubleBoxedMax(), copy.getDoubleBoxedMax(), "Double MAX_VALUE"),
                () -> assertEquals(this.getDoubleBoxedMinNormal(), copy.getDoubleBoxedMinNormal(), "Double MIN_NORMAL"),
                () -> assertEquals(this.getDoubleBoxedNegZero(), copy.getDoubleBoxedNegZero(), "Double -0.0"),
                () -> assertEquals(this.getDoubleBoxedPosZero(), copy.getDoubleBoxedPosZero(), "Double +0.0"),
                () -> assertEquals(this.getCharacterNul(), copy.getCharacterNul(), "Character NUL"),
                () -> assertEquals(this.getCharacterMaxBmp(), copy.getCharacterMaxBmp(), "Character 0xFFFF"),
                () -> assertEquals(this.getStringWithNul(), copy.getStringWithNul(), "String containing NUL"),
                () -> assertEquals(this.getStringWith4ByteUtf8(), copy.getStringWith4ByteUtf8(), "String with 4-byte UTF-8 (U+1F600)")
        );
    }
}
