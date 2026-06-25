package test.eclipse.store.legacy.legacy.cross.data;

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

public class LongLegacy {

    private long to_float;
    private long to_double;
    private long to_short;
    private long to_long;
    private long to_byte;
    private long to_int;
    private long to_boolean;
    private long to_char;
    private long toLong;

    public LongLegacy() {
    }

    public static LongLegacy fillSample() {
        LongLegacy legacy = new LongLegacy();

        legacy.to_float = 1;
        legacy.to_double = 2;
        legacy.to_short = 3;
        legacy.to_long = 4;
        legacy.to_byte = 5;
        legacy.to_int = 6;
        legacy.to_boolean = 0;
        legacy.to_char = 1;
        legacy.toLong = 3;
        return legacy;
    }

    public long getTo_float() {
        return to_float;
    }

    public void setTo_float(long to_float) {
        this.to_float = to_float;
    }

    public long getTo_double() {
        return to_double;
    }

    public void setTo_double(long to_double) {
        this.to_double = to_double;
    }

    public long getTo_short() {
        return to_short;
    }

    public void setTo_short(long to_short) {
        this.to_short = to_short;
    }

    public long getTo_long() {
        return to_long;
    }

    public void setTo_long(long to_long) {
        this.to_long = to_long;
    }

    public long getTo_byte() {
        return to_byte;
    }

    public void setTo_byte(long to_byte) {
        this.to_byte = to_byte;
    }

    public long getTo_int() {
        return to_int;
    }

    public void setTo_int(long to_int) {
        this.to_int = to_int;
    }

    public long getTo_boolean() {
        return to_boolean;
    }

    public void setTo_boolean(long to_boolean) {
        this.to_boolean = to_boolean;
    }

    public long getTo_char() {
        return to_char;
    }

    public void setTo_char(long to_char) {
        this.to_char = to_char;
    }

    public long getToLong() {
        return toLong;
    }

    public void setToLong(long toLong) {
        this.toLong = toLong;
    }

    @Override
    public String toString() {
        return "LongLegacy{" +
                "to_float=" + to_float +
                ", to_double=" + to_double +
                ", to_short=" + to_short +
                ", to_long=" + to_long +
                ", to_byte=" + to_byte +
                ", to_int=" + to_int +
                ", to_boolean=" + to_boolean +
                ", to_char=" + to_char +
                ", toLong=" + toLong +
                '}';
    }
}
