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

public class ByteLegacy
{

    private byte byteTo_float;
    private byte byteTo_double;
    private byte byteTo_short;
    private byte byteTo_long;
    private byte byteTo_byte;
    private byte byteTo_int;
    private byte byteToString;
    private byte byteTo_boolean;
    private byte byteTo_char;
    private byte byteToByte;
    private Byte copyByteTo_byte;

    public ByteLegacy()
    {
    }

    public static ByteLegacy fillSample()
    {
        ByteLegacy legacy = new ByteLegacy();

        legacy.byteTo_float = '1';
        legacy.byteTo_double = '2';
        legacy.byteTo_short = '3';
        legacy.byteTo_long = '4';
        legacy.byteTo_byte = 'a';
        legacy.byteTo_int = '6';
        legacy.byteToString = '5';
        legacy.byteTo_boolean = '0';
        legacy.byteTo_char = 'c';
        legacy.byteToByte = 'x';
        legacy.copyByteTo_byte = 'i';
        return legacy;
    }

    public byte getByteTo_double()
    {
        return byteTo_double;
    }

    @Override
    public String toString()
    {
        return "ByteLegacy{" +
                "byteTo_float=" + byteTo_float +
                ", byteTo_double=" + byteTo_double +
                ", byteTo_short=" + byteTo_short +
                ", byteTo_long=" + byteTo_long +
                ", byteTo_byte=" + byteTo_byte +
                ", byteTo_int=" + byteTo_int +
                ", byteToString=" + byteToString +
                ", byteTo_boolean=" + byteTo_boolean +
                ", byteTo_char=" + byteTo_char +
                ", byteToByte=" + byteToByte +
                ", ByteTo_byte=" + copyByteTo_byte +
                '}';
    }
}
