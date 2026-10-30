package test.eclipse.store.geo.data.generator;

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

import java.util.Arrays;
import java.util.List;

import test.eclipse.store.geo.data.City;
import test.eclipse.store.geo.data.State;


public class GeneratorCZ
{
    public static List<State> generateCZStates()
    {
        State midBohemia = new State("Středočeský kraj", "Prague", 1300000, generateMidBohemiaCities());
        State plzenRegion = new State("Plzeňský kraj", "Plzeň", 580000, generatePlzenRegionCities());
        State southBohemia = new State("Jihočeský kraj", "České Budějovice", 640000, generateSouthBohemiaCities());
        State karlovyVaryRegion = new State("Karlovarský kraj", "Karlovy Vary", 300000, generateKarlovyVaryCities());
        State ustiRegion = new State("Ústecký kraj", "Ústí nad Labem", 820000, generateUstiCities());
        State liberecRegion = new State("Liberecký kraj", "Liberec", 450000, generateLiberecCities());
        State hradecRegion = new State("Královéhradecký kraj", "Hradec Králové", 550000, generateHradecCities());
        State pardubiceRegion = new State("Pardubický kraj", "Pardubice", 520000, generatePardubiceCities());
        State vysocinaRegion = new State("Kraj Vysočina", "Jihlava", 500000, generateVysocinaCities());
        State southMoravia = new State("Jihomoravský kraj", "Brno", 1150000, generateSouthMoraviaCities());
        State olomoucRegion = new State("Olomoucký kraj", "Olomouc", 630000, generateOlomoucCities());
        State moravianSilesian = new State("Moravskoslezský kraj", "Ostrava", 1200000, generateMoravianSilesianCities());
        State zlinRegion = new State("Zlínský kraj", "Zlín", 580000, generateZlinCities());
        return Arrays.asList(midBohemia, plzenRegion, southBohemia, karlovyVaryRegion, ustiRegion, liberecRegion, hradecRegion,
                pardubiceRegion, vysocinaRegion, southMoravia, olomoucRegion, moravianSilesian, zlinRegion);
    }


    public static List<City> generateZlinCities()
    {
        return Arrays.asList(
                new City("Zlín", 74000),
                new City("Uherské Hradiště", 25000),
                new City("Vsetín", 25000),
                new City("Kroměříž", 28000),
                new City("Otrokovice", 18000),
                new City("Rožnov pod Radhoštěm", 16000),
                new City("Holešov", 12000),
                new City("Valašské Meziříčí", 22000),
                new City("Napajedla", 7200),
                new City("Bystřice pod Hostýnem", 8500)
        );
    }

    public static List<City> generateMoravianSilesianCities()
    {
        return Arrays.asList(
                new City("Ostrava", 285000),
                new City("Opava", 56000),
                new City("Frýdek-Místek", 55000),
                new City("Karviná", 50000),
                new City("Havířov", 70000),
                new City("Třinec", 35000),
                new City("Nový Jičín", 23000),
                new City("Kopřivnice", 22000),
                new City("Bohumín", 20000),
                new City("Orlová", 28000)
        );
    }

    public static List<City> generateOlomoucCities()
    {
        return Arrays.asList(
                new City("Olomouc", 100000),
                new City("Přerov", 42000),
                new City("Prostějov", 44000),
                new City("Šumperk", 25000),
                new City("Hranice", 18000),
                new City("Jeseník", 11000),
                new City("Zábřeh", 14000),
                new City("Mohelnice", 9500),
                new City("Litovel", 10000),
                new City("Uničov", 11000)
        );
    }

    public static List<City> generateSouthMoraviaCities()
    {
        return Arrays.asList(
                new City("Brno", 380000),
                new City("Znojmo", 34000),
                new City("Břeclav", 25000),
                new City("Hodonín", 24000),
                new City("Vyškov", 21000),
                new City("Blansko", 20000),
                new City("Kuřim", 11000),
                new City("Boskovice", 11000),
                new City("Mikulov", 7500),
                new City("Kyjov", 11000)
        );
    }

    public static List<City> generateVysocinaCities()
    {
        return Arrays.asList(
                new City("Jihlava", 50000),
                new City("Třebíč", 35000),
                new City("Havlíčkův Brod", 23000),
                new City("Žďár nad Sázavou", 21000),
                new City("Nové Město na Moravě", 10000),
                new City("Pelhřimov", 16000),
                new City("Chotěboř", 10000),
                new City("Telč", 5300),
                new City("Humpolec", 11000),
                new City("Světlá nad Sázavou", 6500)
        );
    }

    public static List<City> generatePardubiceCities()
    {
        return Arrays.asList(
                new City("Pardubice", 90000),
                new City("Chrudim", 23000),
                new City("Svitavy", 17000),
                new City("Ústí nad Orlicí", 15000),
                new City("Litomyšl", 10000),
                new City("Moravská Třebová", 10000),
                new City("Vysoké Mýto", 12000),
                new City("Lanškroun", 10000),
                new City("Polička", 9000),
                new City("Žamberk", 6000)
        );
    }

    public static List<City> generateHradecCities()
    {
        return Arrays.asList(
                new City("Hradec Králové", 93000),
                new City("Trutnov", 31000),
                new City("Náchod", 20000),
                new City("Jičín", 17000),
                new City("Dvůr Králové nad Labem", 16000),
                new City("Nový Bydžov", 7200),
                new City("Jaroměř", 12000),
                new City("Hořice", 9000),
                new City("Rychnov nad Kněžnou", 12000),
                new City("Vrchlabí", 13000)
        );
    }

    public static List<City> generateLiberecCities()
    {
        return Arrays.asList(
                new City("Liberec", 100000),
                new City("Jablonec nad Nisou", 45000),
                new City("Česká Lípa", 37000),
                new City("Turnov", 14000),
                new City("Semily", 8700),
                new City("Nový Bor", 12000),
                new City("Frýdlant", 7600),
                new City("Tanvald", 6600),
                new City("Železný Brod", 6200),
                new City("Hrádek nad Nisou", 7500)
        );
    }

    public static List<City> generateUstiCities()
    {
        return Arrays.asList(
                new City("Ústí nad Labem", 93000),
                new City("Most", 67000),
                new City("Teplice", 50000),
                new City("Děčín", 48000),
                new City("Chomutov", 48000),
                new City("Litvínov", 24000),
                new City("Litoměřice", 24000),
                new City("Žatec", 19000),
                new City("Roudnice nad Labem", 13000),
                new City("Bílina", 15000)
        );
    }


    public static List<City> generateKarlovyVaryCities()
    {
        return Arrays.asList(
                new City("Karlovy Vary", 50000),
                new City("Cheb", 31000),
                new City("Sokolov", 24000),
                new City("Ostrov", 17000),
                new City("Chodov", 14000),
                new City("Aš", 12000),
                new City("Mariánské Lázně", 13000),
                new City("Františkovy Lázně", 6000),
                new City("Nejdek", 8000),
                new City("Kraslice", 6500)
        );
    }


    public static List<City> generateSouthBohemiaCities()
    {
        List<City> southBohemia = Arrays.asList(
                new City("České Budějovice", 94000),
                new City("Tábor", 34000),
                new City("Písek", 30000),
                new City("Jindřichův Hradec", 21000),
                new City("Strakonice", 23000),
                new City("Třeboň", 9000),
                new City("Prachatice", 11000),
                new City("Vimperk", 7500),
                new City("Soběslav", 8000),
                new City("Sezimovo Ústí", 7000)
        );
        return southBohemia;
    }


    public static List<City> generatePlzenRegionCities()
    {
        List<City> plzenRegion = Arrays.asList(
                new City("Plzeň", 175000),
                new City("Klatovy", 23000),
                new City("Domažlice", 11000),
                new City("Rokycany", 14000),
                new City("Tachov", 12500),
                new City("Sušice", 11000),
                new City("Stod", 5000),
                new City("Nýřany", 7000),
                new City("Blovice", 3800),
                new City("Horšovský Týn", 5000)
        );
        return plzenRegion;
    }


    public static List<City> generateMidBohemiaCities()
    {
        List<City> midBohemia = Arrays.asList(
                new City("Kladno", 68600),
                new City("Mladá Boleslav", 45500),
                new City("Příbram", 34800),
                new City("Kolín", 30000),
                new City("Kralupy nad Vltavou", 18000),
                new City("Neratovice", 17000),
                new City("Beroun", 18000),
                new City("Brandýs nad Labem-Stará Boleslav", 17000),
                new City("Český Brod", 10200),
                new City("Poděbrady", 15000),
                new City("Říčany", 16000),
                new City("Kutná Hora", 21000),
                new City("Benešov", 16000),
                new City("Rakovník", 16000),
                new City("Slaný", 17000),
                new City("Řevnice", 4300),
                new City("Jílové u Prahy", 3800),
                new City("Mnichovo Hradiště", 8700),
                new City("Dobříš", 11700),
                new City("Řepy", 4200)
        );
        return midBohemia;
    }

}
