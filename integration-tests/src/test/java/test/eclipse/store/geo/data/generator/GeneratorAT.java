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


public class GeneratorAT
{
	public static List<State> generateATStates()
	{
		State vienna = new State("Wien", "Wien", 1970000, generateViennaCities());
		State lowerAustria = new State("Niederösterreich", "St. Pölten", 1700000, generateLowerAustriaCities());
		State upperAustria = new State("Oberösterreich", "Linz", 1500000, generateUpperAustriaCities());
		State styria = new State("Steiermark", "Graz", 1250000, generateStyriaCities());
		State tyrol = new State("Tirol", "Innsbruck", 760000, generateTyrolCities());
		State carinthia = new State("Kärnten", "Klagenfurt am Wörthersee", 560000, generateCarinthiaCities());
		State salzburg = new State("Salzburg", "Salzburg", 560000, generateSalzburgCities());
		State vorarlberg = new State("Vorarlberg", "Bregenz", 410000, generateVorarlbergCities());
		State burgenland = new State("Burgenland", "Eisenstadt", 300000, generateBurgenlandCities());

		return Arrays.asList(vienna, lowerAustria, upperAustria, styria, tyrol, carinthia, salzburg, vorarlberg, burgenland);
	}

	public static List<City> generateViennaCities()
	{
		return Arrays.asList(
				new City("Wien", 1970000)
		);
	}

	public static List<City> generateLowerAustriaCities()
	{
		return Arrays.asList(
				new City("St. Pölten", 55000),
				new City("Wiener Neustadt", 47000),
				new City("Baden", 26000),
				new City("Krems an der Donau", 25000),
				new City("Amstetten", 24000),
				new City("Mödling", 20000),
				new City("Klosterneuburg", 27000),
				new City("Stockerau", 17000),
				new City("Tulln an der Donau", 17000),
				new City("Schwechat", 18000)
		);
	}

	public static List<City> generateUpperAustriaCities()
	{
		return Arrays.asList(
				new City("Linz", 205000),
				new City("Wels", 62000),
				new City("Steyr", 38000),
				new City("Leonding", 28000),
				new City("Traun", 25000),
				new City("Ansfelden", 17000),
				new City("Marchtrenk", 14000),
				new City("Bad Ischl", 14000),
				new City("Vöcklabruck", 12500),
				new City("Gmunden", 13000)
		);
	}

	public static List<City> generateStyriaCities()
	{
		return Arrays.asList(
				new City("Graz", 300000),
				new City("Leoben", 25000),
				new City("Kapfenberg", 22000),
				new City("Bruck an der Mur", 16000),
				new City("Feldbach", 13000),
				new City("Voitsberg", 9500),
				new City("Knittelfeld", 12500),
				new City("Hartberg", 6500),
				new City("Weiz", 11000),
				new City("Deutschlandsberg", 12000)
		);
	}

	public static List<City> generateTyrolCities()
	{
		return Arrays.asList(
				new City("Innsbruck", 130000),
				new City("Kufstein", 20000),
				new City("Schwaz", 13000),
				new City("Hall in Tirol", 14000),
				new City("Telfs", 16000),
				new City("Lienz", 12000),
				new City("Wörgl", 14000),
				new City("Imst", 11000),
				new City("Reutte", 6600),
				new City("Kitzbühel", 8300)
		);
	}

	public static List<City> generateCarinthiaCities()
	{
		return Arrays.asList(
				new City("Klagenfurt am Wörthersee", 103000),
				new City("Villach", 65000),
				new City("Wolfsberg", 25000),
				new City("Spittal an der Drau", 16000),
				new City("St. Veit an der Glan", 12000),
				new City("Feldkirchen in Kärnten", 14000),
				new City("Hermagor", 7000),
				new City("Völkermarkt", 11000),
				new City("Bleiburg", 4000),
				new City("Radenthein", 6000)
		);
	}

	public static List<City> generateSalzburgCities()
	{
		return Arrays.asList(
				new City("Salzburg", 155000),
				new City("Hallein", 21000),
				new City("Bischofshofen", 11000),
				new City("St. Johann im Pongau", 11000),
				new City("Saalfelden am Steinernen Meer", 17000),
				new City("Zell am See", 10000),
				new City("Seekirchen am Wallersee", 11000),
				new City("Mittersill", 5600),
				new City("Oberndorf bei Salzburg", 6000),
				new City("Neumarkt am Wallersee", 6000)
		);
	}

	public static List<City> generateVorarlbergCities()
	{
		return Arrays.asList(
				new City("Bregenz", 30000),
				new City("Dornbirn", 50000),
				new City("Feldkirch", 35000),
				new City("Bludenz", 14000),
				new City("Hohenems", 17000),
				new City("Lustenau", 23000),
				new City("Rankweil", 12000),
				new City("Götzis", 11000),
				new City("Hard", 13000),
				new City("Altach", 7000)
		);
	}

	public static List<City> generateBurgenlandCities()
	{
		return Arrays.asList(
				new City("Eisenstadt", 15000),
				new City("Oberwart", 7000),
				new City("Mattersburg", 7200),
				new City("Neusiedl am See", 8000),
				new City("Jennersdorf", 4200),
				new City("Pinkafeld", 5500),
				new City("Rust", 2000),
				new City("Güssing", 3700),
				new City("Frauenkirchen", 3000),
				new City("Parndorf", 5100)
		);
	}
}
