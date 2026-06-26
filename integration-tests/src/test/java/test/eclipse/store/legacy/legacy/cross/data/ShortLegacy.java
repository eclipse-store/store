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

public class ShortLegacy
{

    private short to_float;
    private short to_double;
    private short to_short;
    private short to_long;
    private short to_byte;
    private short to_int;
    private short to_boolean;
    private short to_char;
    private short toShort;

    public ShortLegacy()
    {
    }

    public static ShortLegacy fillSample()
    {
        ShortLegacy legacy = new ShortLegacy();

        legacy.to_float = 1;
        legacy.to_double = 2;
        legacy.to_short = 3;
        legacy.to_long = 4;
        legacy.to_byte = 5;
        legacy.to_int = 6;
        legacy.to_boolean = 0;
        legacy.to_char = 1;
        legacy.toShort = 3;
        return legacy;
    }

    public short getTo_float()
    {
        return to_float;
    }

    public void setTo_float(short to_float)
    {
        this.to_float = to_float;
    }

    public short getTo_double()
    {
        return to_double;
    }

    public void setTo_double(short to_double)
    {
        this.to_double = to_double;
    }

    public short getTo_short()
    {
        return to_short;
    }

    public void setTo_short(short to_short)
    {
        this.to_short = to_short;
    }

    public short getTo_long()
    {
        return to_long;
    }

    public void setTo_long(short to_long)
    {
        this.to_long = to_long;
    }

    public short getTo_byte()
    {
        return to_byte;
    }

    public void setTo_byte(short to_byte)
    {
        this.to_byte = to_byte;
    }

    public short getTo_int()
    {
        return to_int;
    }

    public void setTo_int(short to_int)
    {
        this.to_int = to_int;
    }

    public short getTo_boolean()
    {
        return to_boolean;
    }

    public void setTo_boolean(short to_boolean)
    {
        this.to_boolean = to_boolean;
    }

    public short getTo_char()
    {
        return to_char;
    }

    public void setTo_char(short to_char)
    {
        this.to_char = to_char;
    }

    public short getToShort()
    {
        return toShort;
    }

    public void setToShort(short toShort)
    {
        this.toShort = toShort;
    }

    @Override
    public String toString()
    {
        return "ShortLegacy{" +
                "to_float=" + to_float +
                ", to_double=" + to_double +
                ", to_short=" + to_short +
                ", to_long=" + to_long +
                ", to_byte=" + to_byte +
                ", to_int=" + to_int +
                ", to_boolean=" + to_boolean +
                ", to_char=" + to_char +
                ", toShort=" + toShort +
                '}';
    }
}
