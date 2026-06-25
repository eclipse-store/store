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


public class GeneratorDE
{


	public static List<State> generateDEStates()
	{
		State bavaria = new State("Bavaria", "Munich", 13000000, generateBavariaCities());
		State badenWuerttemberg = new State("Baden-Württemberg", "Stuttgart", 11200000, generateBadenWuerttembergCities());
		State berlin = new State("Berlin", "Berlin", 3700000, generateBerlinCities());
		State saarland = new State("Saarland", "Saarbrücken", 990000, generateSaarlandCities());
		State brandenburg = new State("Brandenburg", "Potsdam", 2500000, generateBrandenburgCities());
		State bremen = new State("Bremen", "Bremen", 680000, generateBremenCities());
		State hamburg = new State("Hamburg", "Hamburg", 1900000, generateHamburgCities());
		State hessen = new State("Hessen", "Wiesbaden", 6300000, generateHessenCities());
		State mecklenburg = new State("Mecklenburg-Vorpommern", "Schwerin", 1600000, generateMecklenburgCities());
		State nrw = new State("Nordrhein-Westfalen", "Düsseldorf", 17900000, generateNRWCities());
		State saxony = new State("Sachsen", "Dresden", 4050000, generateSaxonyCities());
		State rhinelandPalatinate = new State("Rheinland-Pfalz", "Mainz", 4100000, generateRhinelandPalatinateCities());
		State saxonyAnhalt = new State("Sachsen-Anhalt", "Magdeburg", 2200000, generateSaxonyAnhaltCities());
		State schleswigHolstein = new State("Schleswig-Holstein", "Kiel", 2900000, generateSchleswigHolsteinCities());
		State thuringia = new State("Thüringen", "Erfurt", 2100000, generateThuringiaCities());
		State lowerSaxony = new State("Niedersachsen", "Hannover", 8000000, generateLowerSaxonyCities());


		return Arrays.asList(bavaria, badenWuerttemberg, berlin, brandenburg, bremen, hamburg, hessen, mecklenburg,
				nrw, saxony, rhinelandPalatinate, saxonyAnhalt, schleswigHolstein, thuringia, lowerSaxony);
	}

	// Baden-Württemberg

	public static List<City> generateBadenWuerttembergCities() {
		return Arrays.asList(
				new City("Stuttgart", 630000),
				new City("Mannheim", 310000),
				new City("Karlsruhe", 310000),
				new City("Freiburg im Breisgau", 230000),
				new City("Heidelberg", 160000),
				new City("Heilbronn", 130000),
				new City("Ulm", 125000),
				new City("Pforzheim", 125000),
				new City("Reutlingen", 115000),
				new City("Tübingen", 90000)
		);
	}

	// Bayern

	public static List<City> generateBavariaCities() {
		return Arrays.asList(
				new City("München", 1500000),
				new City("Nürnberg", 510000),
				new City("Augsburg", 300000),
				new City("Regensburg", 150000),
				new City("Ingolstadt", 140000),
				new City("Fürth", 130000),
				new City("Würzburg", 130000),
				new City("Erlangen", 110000),
				new City("Bayreuth", 75000),
				new City("Bamberg", 78000)
		);
	}

	// Berlin

	public static List<City> generateBerlinCities() {
		return Arrays.asList(
				new City("Berlin", 3700000)
		);
	}

	// Brandenburg


	public static List<City> generateBrandenburgCities() {
		return Arrays.asList(
				new City("Potsdam", 185000),
				new City("Cottbus", 100000),
				new City("Brandenburg an der Havel", 72000),
				new City("Frankfurt (Oder)", 58000),
				new City("Oranienburg", 45000),
				new City("Eberswalde", 42000),
				new City("Bernau bei Berlin", 42000),
				new City("Fürstenwalde", 33000),
				new City("Neuruppin", 32000),
				new City("Schwedt", 30000)
		);
	}

	// Bremen


	public static List<City> generateBremenCities() {
		return Arrays.asList(
				new City("Bremen", 560000),
				new City("Bremerhaven", 120000)
		);
	}

	// Hamburg


	public static List<City> generateHamburgCities() {
		return Arrays.asList(
				new City("Hamburg", 1900000)
		);
	}

	// Hessen


	public static List<City> generateHessenCities() {
		return Arrays.asList(
				new City("Frankfurt am Main", 770000),
				new City("Wiesbaden", 280000),
				new City("Kassel", 200000),
				new City("Darmstadt", 160000),
				new City("Offenbach am Main", 140000),
				new City("Hanau", 100000),
				new City("Gießen", 90000),
				new City("Marburg", 77000),
				new City("Fulda", 70000),
				new City("Rüsselsheim", 65000)
		);
	}

	// Mecklenburg-Vorpommern


	public static List<City> generateMecklenburgCities() {
		return Arrays.asList(
				new City("Schwerin", 95000),
				new City("Rostock", 210000),
				new City("Neubrandenburg", 65000),
				new City("Greifswald", 60000),
				new City("Stralsund", 59000),
				new City("Wismar", 42000),
				new City("Güstrow", 30000),
				new City("Waren (Müritz)", 21000),
				new City("Parchim", 17000),
				new City("Ribnitz-Damgarten", 15000)
		);
	}

	// Niedersachsen

	public static List<City> generateLowerSaxonyCities() {
		return Arrays.asList(
				new City("Hannover", 540000),
				new City("Braunschweig", 250000),
				new City("Oldenburg", 170000),
				new City("Osnabrück", 165000),
				new City("Wolfsburg", 125000),
				new City("Göttingen", 120000),
				new City("Salzgitter", 100000),
				new City("Hildesheim", 100000),
				new City("Lüneburg", 75000),
				new City("Emden", 50000)
		);
	}

	// Nordrhein-Westfalen


	public static List<City> generateNRWCities() {
		return Arrays.asList(
				new City("Köln", 1100000),
				new City("Düsseldorf", 620000),
				new City("Dortmund", 600000),
				new City("Essen", 580000),
				new City("Duisburg", 500000),
				new City("Bochum", 365000),
				new City("Wuppertal", 355000),
				new City("Bielefeld", 340000),
				new City("Bonn", 330000),
				new City("Münster", 320000)
		);
	}

	// Rheinland-Pfalz


	public static List<City> generateRhinelandPalatinateCities() {
		return Arrays.asList(
				new City("Mainz", 220000),
				new City("Ludwigshafen am Rhein", 170000),
				new City("Koblenz", 115000),
				new City("Trier", 110000),
				new City("Kaiserslautern", 100000),
				new City("Worms", 85000),
				new City("Neuwied", 65000),
				new City("Speyer", 50000),
				new City("Landau in der Pfalz", 47000),
				new City("Frankenthal", 48000)
		);
	}

	// Saarland
	public static List<City> generateSaarlandCities() {
		return Arrays.asList(
				new City("Saarbrücken", 180000),
				new City("Neunkirchen", 47000),
				new City("Homburg", 42000),
				new City("Völklingen", 39000),
				new City("St. Ingbert", 37000),
				new City("Saarlouis", 35000),
				new City("Merzig", 30000),
				new City("Blieskastel", 22000),
				new City("Sankt Wendel", 26000),
				new City("Dillingen/Saar", 20000)
		);
	}

	// Sachsen


	public static List<City> generateSaxonyCities() {
		return Arrays.asList(
				new City("Dresden", 560000),
				new City("Leipzig", 620000),
				new City("Chemnitz", 245000),
				new City("Zwickau", 90000),
				new City("Plauen", 65000),
				new City("Görlitz", 56000),
				new City("Freiberg", 42000),
				new City("Pirna", 38000),
				new City("Bautzen", 39000),
				new City("Hoyerswerda", 32000)
		);
	}

	// Sachsen-Anhalt


	public static List<City> generateSaxonyAnhaltCities() {
		return Arrays.asList(
				new City("Magdeburg", 240000),
				new City("Halle (Saale)", 240000),
				new City("Dessau-Roßlau", 80000),
				new City("Wittenberg", 47000),
				new City("Bernburg", 34000),
				new City("Halberstadt", 40000),
				new City("Stendal", 40000),
				new City("Merseburg", 34000),
				new City("Naumburg (Saale)", 33000),
				new City("Bitterfeld-Wolfen", 37000)
		);
	}

	// Schleswig-Holstein


	public static List<City> generateSchleswigHolsteinCities() {
		return Arrays.asList(
				new City("Kiel", 250000),
				new City("Lübeck", 220000),
				new City("Flensburg", 90000),
				new City("Neumünster", 80000),
				new City("Norderstedt", 80000),
				new City("Elmshorn", 50000),
				new City("Pinneberg", 50000),
				new City("Itzehoe", 32000),
				new City("Wedel", 33000),
				new City("Ahrensburg", 32000)
		);
	}

	// Thüringen


	public static List<City> generateThuringiaCities() {
		return Arrays.asList(
				new City("Erfurt", 215000),
				new City("Jena", 110000),
				new City("Gera", 95000),
				new City("Weimar", 65000),
				new City("Gotha", 45000),
				new City("Eisenach", 42000),
				new City("Suhl", 35000),
				new City("Nordhausen", 42000),
				new City("Ilmenau", 38000),
				new City("Meiningen", 21000)
		);
	}

}
