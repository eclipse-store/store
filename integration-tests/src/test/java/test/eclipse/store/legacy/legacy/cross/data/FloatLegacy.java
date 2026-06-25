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

public class FloatLegacy {

    private float to_float;
    private float to_double;
    private float to_short;
    private float to_long;
    private float to_byte;
    private float to_int;
    private float to_boolean;
    private float to_char;
    private float toFloat;

    public FloatLegacy() {
    }

    public static FloatLegacy fillSample() {
        FloatLegacy legacy = new FloatLegacy();

        legacy.to_float = 1;
        legacy.to_double = 2;
        legacy.to_short = 3;
        legacy.to_long = 4;
        legacy.to_byte = 5;
        legacy.to_int = 6;
        legacy.to_boolean = 0;
        legacy.to_char = 1;
        legacy.toFloat = 3;
        return legacy;
    }

    public float getTo_float() {
        return to_float;
    }

    public void setTo_float(float to_float) {
        this.to_float = to_float;
    }

    public float getTo_double() {
        return to_double;
    }

    public void setTo_double(float to_double) {
        this.to_double = to_double;
    }

    public float getTo_short() {
        return to_short;
    }

    public void setTo_short(float to_short) {
        this.to_short = to_short;
    }

    public float getTo_long() {
        return to_long;
    }

    public void setTo_long(float to_long) {
        this.to_long = to_long;
    }

    public float getTo_byte() {
        return to_byte;
    }

    public void setTo_byte(float to_byte) {
        this.to_byte = to_byte;
    }

    public float getTo_int() {
        return to_int;
    }

    public void setTo_int(float to_int) {
        this.to_int = to_int;
    }

    public float getTo_boolean() {
        return to_boolean;
    }

    public void setTo_boolean(float to_boolean) {
        this.to_boolean = to_boolean;
    }

    public float getTo_char() {
        return to_char;
    }

    public void setTo_char(float to_char) {
        this.to_char = to_char;
    }

    public float getToFloat() {
        return toFloat;
    }

    public void setToFloat(float toFloat) {
        this.toFloat = toFloat;
    }

    @Override
    public String toString() {
        return "FloatLegacy{" +
                "to_float=" + to_float +
                ", to_double=" + to_double +
                ", to_short=" + to_short +
                ", to_long=" + to_long +
                ", to_byte=" + to_byte +
                ", to_int=" + to_int +
                ", to_boolean=" + to_boolean +
                ", to_char=" + to_char +
                ", toFloat=" + toFloat +
                '}';
    }
}
