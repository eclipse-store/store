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

import java.text.DateFormatSymbols;

public enum BonusMonat
{
    // (20.02.2012 TM)NOTE: Monatsstrings sollten mal dynamisch wartbar konsolidiert werden.
    Januar   ("Jan",  1, 12, "Januar")   ,
    Februar  ("Feb",  2,  1, "Februar")  ,
    Maerz    ("Mrz",  3,  2, "März")     , // verdammte Sondefälle @ Kürzel ^^
    April    ("Apr",  4,  3, "April")    ,
    Mai      ("Mai",  5,  4, "Mai")      ,
    Juni     ("Jun",  6,  5, "Juni")     ,
    Juli     ("Jul",  7,  6, "Juli")     ,
    August   ("Aug",  8,  7, "August")   ,
    September("Sep",  9,  8, "September"),
    Oktober  ("Okt", 10,  9, "Oktober")  ,
    November ("Nov", 11, 10, "November") ,
    Dezember ("Dez", 12, 11, "Dezember") ;



    ///////////////////////////////////////////////////////////////////////////
    // static methods  //
    ///////////////////

    public static final int orderByKonditionsindex(final BonusMonat n1, final BonusMonat m2)
    {
        // null Sonderfälle
        if(n1 == null)
        {
            return m2 == null ? 0 : -1;
        }
        if(m2 == null)
        {
            return 1;
        }

        // Normalfälle (mal in lustiger kompakter Schreibweise: Fälle definieren, Werte anhängen)
        return m2.konditionsindex >= n1.konditionsindex ? m2.konditionsindex != n1.konditionsindex ? -1 : 0 : 1;
    }


    public static BonusMonat fromKalenderindex(final int kalenderindex)
    {
        // CHECKSTYLE.OFF: MagicNumber: Blanke Indexwerte
        switch(kalenderindex)
        {
            case  1:
                return Januar;
            case  2:
                return Februar;
            case  3:
                return Maerz;
            case  4:
                return April;
            case  5:
                return Mai;
            case  6:
                return Juni;
            case  7:
                return Juli;
            case  8:
                return August;
            case  9:
                return September;
            case 10:
                return Oktober;
            case 11:
                return November;
            case 12:
                return Dezember;
            default:
                throw new IllegalArgumentException("Kein Kalendermonat: " + kalenderindex);
        }
        // CHECKSTYLE.ON: MagicNumber
    }

    public static BonusMonat fromKonditionsindex(final int abrechnungsindex)
    {
        // CHECKSTYLE.OFF: MagicNumber: Blanke Indexwerte
        switch(abrechnungsindex)
        {
            case  1:
                return Februar;
            case  2:
                return Maerz;
            case  3:
                return April;
            case  4:
                return Mai;
            case  5:
                return Juni;
            case  6:
                return Juli;
            case  7:
                return August;
            case  8:
                return September;
            case  9:
                return Oktober;
            case 10:
                return November;
            case 11:
                return Dezember;
            case 12:
                return Januar;
            default:
                throw new IllegalArgumentException("Kein Konditionsmonat: " + abrechnungsindex);
        }
        // CHECKSTYLE.ON: MagicNumber
    }



    ///////////////////////////////////////////////////////////////////////////
    // instance fields //
    ////////////////////

    private final String           kuerzel        ;
    private final int              kalenderindex  ;
    private final int              konditionsindex;
    private final BonusRegulierung first          ;
    private final BonusRegulierung last           ;
    private final String           description    ;



    ///////////////////////////////////////////////////////////////////////////
    // constructors //
    /////////////////

    private BonusMonat(final String kuerzel, final int kalenderindex, final int abrechnungsindex, final String description)
    {
        this.kuerzel         = kuerzel;
        this.kalenderindex   = kalenderindex;
        this.konditionsindex = abrechnungsindex;
        this.first           = BonusRegulierung.fromNummer(2 * abrechnungsindex - 1);
        this.last            = BonusRegulierung.fromNummer(2 * abrechnungsindex    );
        this.description     = description;
    }




    ///////////////////////////////////////////////////////////////////////////
    // getters          //
    /////////////////////

    public BonusMonat prev()
    {
        if(this == Januar)
        {
            return Dezember;
        }
        return fromKalenderindex(this.kalenderindex - 1);
    }

    public BonusMonat next()
    {
        if(this == Dezember)
        {
            return Januar;
        }
        return fromKalenderindex(this.kalenderindex + 1);
    }

    /**
     * Wird verwendet für Unterordner und für DTA Verwendungszweck
     *
     * @return dreibuchstabiges Kürzel des Monatsnamens (Jan, Feb, Mrz, usw.)
     */
    public String token()
    {
        return this.kuerzel;
    }

    public int kalenderindex()
    {
        return this.kalenderindex;
    }

    public int konditionsindex()
    {
        return this.konditionsindex;
    }

    public BonusRegulierung firstRegulierung()
    {
        return this.first;
    }

    public BonusRegulierung lastRegulierung()
    {
        return this.last;
    }

    public String toLocaleString()
    {
        final DateFormatSymbols format = DateFormatSymbols.getInstance();
        return format.getMonths()[this.kalenderindex - 1]; // immer schön alles umkopieren für jeden Aufruf @Sun o_0
    }

    public String toShortLocaleString()
    {
        final DateFormatSymbols format = DateFormatSymbols.getInstance();
        return format.getShortMonths()[this.kalenderindex - 1]; // immer schön alles umkopieren für jeden Aufruf @Sun o_0
    }

    public String description()
    {
        return this.description;
    }

}
