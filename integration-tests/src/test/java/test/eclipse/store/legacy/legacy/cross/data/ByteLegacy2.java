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

public class ByteLegacy2 {

    private float byteTo_float;
    private double byteTo_double;
    private short byteTo_short;
    private long byteTo_long;
    private byte byteTo_byte;
    private int byteTo_int;
    private char byteToString;
    private boolean byteTo_boolean;
    private char byteTo_char;
    private Byte bytToByte;
    private byte copyByteTo_byte;

    public ByteLegacy2() {
    }

    public double getByteTo_double() {
        return byteTo_double;
    }

    @Override
    public String toString() {
        return "ByteLegacy2{" +
                "byteTo_float=" + byteTo_float +
                ", byteTo_double=" + byteTo_double +
                ", byteTo_short=" + byteTo_short +
                ", byteTo_long=" + byteTo_long +
                ", byteTo_byte=" + byteTo_byte +
                ", byteTo_int=" + byteTo_int +
                ", byteToString=" + byteToString +
                ", byteTo_boolean=" + byteTo_boolean +
                ", byteTo_char=" + byteTo_char +
                ", bytToByte=" + bytToByte +
                ", copyByteTo_byte=" + copyByteTo_byte +
                '}';
    }
}
