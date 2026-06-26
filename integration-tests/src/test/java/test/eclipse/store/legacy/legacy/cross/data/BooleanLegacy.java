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

public class BooleanLegacy
{

    private boolean to_float;
    private boolean to_double;
    private boolean to_short;
    private boolean to_long;
    private boolean to_byte;
    private boolean to_int;
    private boolean to_boolean;
    private boolean to_char;
    private boolean toBoolean;
    private Boolean copyBooleanTo_boolean;

    public BooleanLegacy()
    {
    }

    public static BooleanLegacy fillSample()
    {
        BooleanLegacy legacy = new BooleanLegacy();

        legacy.to_float = false;
        legacy.to_double = true;
        legacy.to_short = false;
        legacy.to_long = true;
        legacy.to_byte = false;
        legacy.to_int = true;
        legacy.to_boolean = true;
        legacy.to_char = false;
        legacy.toBoolean = true;
        legacy.copyBooleanTo_boolean = true;
        return legacy;
    }

    public boolean isTo_float()
    {
        return to_float;
    }

    public void setTo_float(boolean to_float)
    {
        this.to_float = to_float;
    }

    public boolean isTo_double()
    {
        return to_double;
    }

    public void setTo_double(boolean to_double)
    {
        this.to_double = to_double;
    }

    public boolean isTo_short()
    {
        return to_short;
    }

    public void setTo_short(boolean to_short)
    {
        this.to_short = to_short;
    }

    public boolean isTo_long()
    {
        return to_long;
    }

    public void setTo_long(boolean to_long)
    {
        this.to_long = to_long;
    }

    public boolean isTo_byte()
    {
        return to_byte;
    }

    public void setTo_byte(boolean to_byte)
    {
        this.to_byte = to_byte;
    }

    public boolean isTo_int()
    {
        return to_int;
    }

    public void setTo_int(boolean to_int)
    {
        this.to_int = to_int;
    }

    public boolean isTo_boolean()
    {
        return to_boolean;
    }

    public void setTo_boolean(boolean to_boolean)
    {
        this.to_boolean = to_boolean;
    }

    public boolean isTo_char()
    {
        return to_char;
    }

    public void setTo_char(boolean to_char)
    {
        this.to_char = to_char;
    }

    public boolean isToBoolean()
    {
        return toBoolean;
    }

    public void setToBoolean(boolean toBoolean)
    {
        this.toBoolean = toBoolean;
    }

    @Override
    public String toString()
    {
        return "BooleanLegacy{" +
                "to_float=" + to_float +
                ", to_double=" + to_double +
                ", to_short=" + to_short +
                ", to_long=" + to_long +
                ", to_byte=" + to_byte +
                ", to_int=" + to_int +
                ", to_boolean=" + to_boolean +
                ", to_char=" + to_char +
                ", toBoolean=" + toBoolean +
                ", copyBooleanTo_boolean=" + copyBooleanTo_boolean +
                '}';
    }
}
