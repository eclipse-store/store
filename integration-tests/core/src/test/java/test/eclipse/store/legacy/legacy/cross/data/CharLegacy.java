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

public class CharLegacy
{

    private char charTo_float;
    private char charTo_double;
    private char charTo_short;
    private char charTo_long;
    private char charTo_byte;
    private char charTo_int;
    private char charToString;
    private char charTo_boolean;
    private char charTo_char;
    private char charToCharacter;

    public CharLegacy()
    {
    }

    public static CharLegacy fillSample()
    {
        CharLegacy charLegacy = new CharLegacy();

        charLegacy.charTo_float = '1';
        charLegacy.charTo_double = '2';
        charLegacy.charTo_short = '3';
        charLegacy.charTo_long = '4';
        charLegacy.charTo_byte = 'č';
        charLegacy.charTo_int = '6';
        charLegacy.charToString = '5';
        charLegacy.charTo_boolean = '0';
        charLegacy.charTo_char = 'c';
        charLegacy.charToCharacter = 'x';
        return charLegacy;
    }

    public char getCharTo_float()
    {
        return charTo_float;
    }

    public void setCharTo_float(char charTo_float)
    {
        this.charTo_float = charTo_float;
    }

    public char getCharTo_double()
    {
        return charTo_double;
    }

    public void setCharTo_double(char charTo_double)
    {
        this.charTo_double = charTo_double;
    }

    public char getCharTo_short()
    {
        return charTo_short;
    }

    public void setCharTo_short(char charTo_short)
    {
        this.charTo_short = charTo_short;
    }

    public char getCharTo_long()
    {
        return charTo_long;
    }

    public void setCharTo_long(char charTo_long)
    {
        this.charTo_long = charTo_long;
    }

    public char getCharTo_byte()
    {
        return charTo_byte;
    }

    public void setCharTo_byte(char charTo_byte)
    {
        this.charTo_byte = charTo_byte;
    }

    public char getCharTo_int()
    {
        return charTo_int;
    }

    public void setCharTo_int(char charTo_int)
    {
        this.charTo_int = charTo_int;
    }

    public char getCharToString()
    {
        return charToString;
    }

    public void setCharToString(char charToString)
    {
        this.charToString = charToString;
    }

    public char getCharTo_boolean()
    {
        return charTo_boolean;
    }

    public void setCharTo_boolean(char charTo_boolean)
    {
        this.charTo_boolean = charTo_boolean;
    }

    public char getCharTo_char()
    {
        return charTo_char;
    }

    public void setCharTo_char(char charTo_char)
    {
        this.charTo_char = charTo_char;
    }

    public char getCharToCharacter()
    {
        return charToCharacter;
    }

    public void setCharToCharacter(char charToCharacter)
    {
        this.charToCharacter = charToCharacter;
    }

    @Override
    public String toString()
    {
        return "CharLegacy{" +
                "charTo_float=" + charTo_float +
                ", charTo_double=" + charTo_double +
                ", charTo_short=" + charTo_short +
                ", charTo_long=" + charTo_long +
                ", charTo_byte=" + charTo_byte +
                ", charTo_int=" + charTo_int +
                ", charToString=" + charToString +
                ", charTo_boolean=" + charTo_boolean +
                ", charTo_char=" + charTo_char +
                ", charToCharacter=" + charToCharacter +
                '}';
    }
}
