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

public class DoubleLegacy
{

    private double to_float;
    private double to_double;
    private double to_short;
    private double to_long;
    private double to_byte;
    private double to_int;
    private double to_boolean;
    private double to_char;
    private double toDouble;

    public DoubleLegacy()
    {
    }

    public static DoubleLegacy fillSample()
    {
        DoubleLegacy legacy = new DoubleLegacy();

        legacy.to_float = 1;
        legacy.to_double = 2;
        legacy.to_short = 3;
        legacy.to_long = 4;
        legacy.to_byte = 5;
        legacy.to_int = 6;
        legacy.to_boolean = 0;
        legacy.to_char = 1;
        legacy.toDouble = 3;
        return legacy;
    }

    public double getTo_float()
    {
        return to_float;
    }

    public void setTo_float(double to_float)
    {
        this.to_float = to_float;
    }

    public double getTo_double()
    {
        return to_double;
    }

    public void setTo_double(double to_double)
    {
        this.to_double = to_double;
    }

    public double getTo_short()
    {
        return to_short;
    }

    public void setTo_short(double to_short)
    {
        this.to_short = to_short;
    }

    public double getTo_long()
    {
        return to_long;
    }

    public void setTo_long(double to_long)
    {
        this.to_long = to_long;
    }

    public double getTo_byte()
    {
        return to_byte;
    }

    public void setTo_byte(double to_byte)
    {
        this.to_byte = to_byte;
    }

    public double getTo_int()
    {
        return to_int;
    }

    public void setTo_int(double to_int)
    {
        this.to_int = to_int;
    }

    public double getTo_boolean()
    {
        return to_boolean;
    }

    public void setTo_boolean(double to_boolean)
    {
        this.to_boolean = to_boolean;
    }

    public double getTo_char()
    {
        return to_char;
    }

    public void setTo_char(double to_char)
    {
        this.to_char = to_char;
    }

    public double getToDouble()
    {
        return toDouble;
    }

    public void setToDouble(double toDouble)
    {
        this.toDouble = toDouble;
    }

    @Override
    public String toString()
    {
        return "DoubleLegacy{" +
                "to_float=" + to_float +
                ", to_double=" + to_double +
                ", to_short=" + to_short +
                ", to_long=" + to_long +
                ", to_byte=" + to_byte +
                ", to_int=" + to_int +
                ", to_boolean=" + to_boolean +
                ", to_char=" + to_char +
                ", toDouble=" + toDouble +
                '}';
    }
}
