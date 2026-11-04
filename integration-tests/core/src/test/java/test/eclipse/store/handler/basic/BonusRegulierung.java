package test.eclipse.store.handler.basic;

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

public enum BonusRegulierung
{
    Regulierung01(1),
    Regulierung02(2),
    Regulierung03(3),
    Regulierung04(4),
    Regulierung05(5),
    Regulierung06(6),
    Regulierung07(7),
    Regulierung08(8),
    Regulierung09(9),
    Regulierung10(10),
    Regulierung11(11),
    Regulierung12(12),
    Regulierung13(13),
    Regulierung14(14),
    Regulierung15(15),
    Regulierung16(16),
    Regulierung17(17),
    Regulierung18(18),
    Regulierung19(19),
    Regulierung20(20),
    Regulierung21(21),
    Regulierung22(22),
    Regulierung23(23),
    Regulierung24(24);


    ///////////////////////////////////////////////////////////////////////////
    // static methods //

    /// ////////////////

    public static BonusRegulierung fromNummer(final int nummer)
    {
        // CHECKSTYLE.OFF: MagicNumber: Blanke Indexwerte.
        switch (nummer) {
            case 1:
                return Regulierung01;
            case 2:
                return Regulierung02;
            case 3:
                return Regulierung03;
            case 4:
                return Regulierung04;
            case 5:
                return Regulierung05;
            case 6:
                return Regulierung06;
            case 7:
                return Regulierung07;
            case 8:
                return Regulierung08;
            case 9:
                return Regulierung09;
            case 10:
                return Regulierung10;
            case 11:
                return Regulierung11;
            case 12:
                return Regulierung12;
            case 13:
                return Regulierung13;
            case 14:
                return Regulierung14;
            case 15:
                return Regulierung15;
            case 16:
                return Regulierung16;
            case 17:
                return Regulierung17;
            case 18:
                return Regulierung18;
            case 19:
                return Regulierung19;
            case 20:
                return Regulierung20;
            case 21:
                return Regulierung21;
            case 22:
                return Regulierung22;
            case 23:
                return Regulierung23;
            case 24:
                return Regulierung24;
            default:
                throw new IllegalArgumentException("Ungültige Regulierung: " + nummer);
        }
        // CHECKSTYLE.ON: MagicNumber
    }

    public static final BonusRegulierung first()
    {
        return Regulierung01;
    }

    public static final BonusRegulierung last()
    {
        return Regulierung24;
    }

    public BonusRegulierung prev()
    {
        if (this == first()) {
            return null;
        }
        return fromNummer(this.nummer - 1);
    }

    public BonusRegulierung next()
    {
        if (this == last()) {
            return null;
        }
        return fromNummer(this.nummer + 1);
    }

    public final boolean isBefore(final BonusRegulierung other)
    {
        return this.nummer < other.nummer;
    }

    public final boolean isAfter(final BonusRegulierung other)
    {
        return this.nummer > other.nummer;
    }

    public final boolean isEqual(final BonusRegulierung other)
    {
        return this == other;
    }

    public static final BonusRegulierung latest(final BonusRegulierung r1, final BonusRegulierung r2)
    {
        return r1 == null ? r2 : r2 == null ? r1 : r1.isAfter(r2) ? r1 : r2;
    }

    public static final BonusRegulierung earliest(final BonusRegulierung r1, final BonusRegulierung r2)
    {
        return r1 == null ? r2 : r2 == null ? r1 : r1.isBefore(r2) ? r1 : r2;
    }


    ///////////////////////////////////////////////////////////////////////////
    // instance fields //
    /// /////////////////

    private final int nummer;


    ///////////////////////////////////////////////////////////////////////////
    // constructors //

    /// //////////////

    private BonusRegulierung(final int nummer)
    {
        this.nummer = nummer;
    }


    ///////////////////////////////////////////////////////////////////////////
    // declared methods //

    /// //////////////////

    public int nummer()
    {
        return this.nummer;
    }


    public BonusMonat monat()
    {
        // kann nicht als final member gecacht werden, da Monat das seinerseits mit Regulierung tut
        switch (this) {
            case Regulierung01:
                return BonusMonat.Februar;
            case Regulierung02:
                return BonusMonat.Februar;
            case Regulierung03:
                return BonusMonat.Maerz;
            case Regulierung04:
                return BonusMonat.Maerz;
            case Regulierung05:
                return BonusMonat.April;
            case Regulierung06:
                return BonusMonat.April;
            case Regulierung07:
                return BonusMonat.Mai;
            case Regulierung08:
                return BonusMonat.Mai;
            case Regulierung09:
                return BonusMonat.Juni;
            case Regulierung10:
                return BonusMonat.Juni;
            case Regulierung11:
                return BonusMonat.Juli;
            case Regulierung12:
                return BonusMonat.Juli;
            case Regulierung13:
                return BonusMonat.August;
            case Regulierung14:
                return BonusMonat.August;
            case Regulierung15:
                return BonusMonat.September;
            case Regulierung16:
                return BonusMonat.September;
            case Regulierung17:
                return BonusMonat.Oktober;
            case Regulierung18:
                return BonusMonat.Oktober;
            case Regulierung19:
                return BonusMonat.November;
            case Regulierung20:
                return BonusMonat.November;
            case Regulierung21:
                return BonusMonat.Dezember;
            case Regulierung22:
                return BonusMonat.Dezember;
            case Regulierung23:
                return BonusMonat.Januar;
            case Regulierung24:
                return BonusMonat.Januar;
            default:
                throw new UnsupportedOperationException("Implementierungsfehler für " + this);
        }
    }

    @Override
    public String toString()
    {
        return BonusRegulierung.class.getSimpleName() + " " + this.nummer;
    }

}
