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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import test.eclipse.store.geo.data.City;
import test.eclipse.store.geo.data.Country;
import test.eclipse.store.geo.data.Geo;


public class Generator
{

	public static Geo generateGeo()
	{
		return new Geo(generateCountries());
	}


	public static List<Country> generateCountries()
	{
		Country czechia = new Country("Czechia", GeneratorCZ.generateCZStates());
		Country germany = new Country("Germany", GeneratorDE.generateDEStates());
		ArrayList<Country> countries = new ArrayList<Country>();
		countries.add(czechia);
		countries.add(germany);
		return countries;
	}




	public static List<City> generateBavariaCities()
	{
		List<City> bavariaCities = Arrays.asList(
				new City ("Munich", 1500000),
				new City("Nuremberg", 520000),
				new City("Augsburg", 300000),
				new City("Regensburg", 155000),
				new City("Ingolstadt", 140000),
				new City("Würzburg", 130000),
				new City("Fürth", 130000),
				new City("Erlangen", 115000),
				new City("Bayreuth", 75000),
				new City("Bamberg", 80000),
				new City("Aschaffenburg", 71000),
				new City("Rosenheim", 65000),
				new City("Landshut", 75000),
				new City("Passau", 55000),
				new City("Kempten (Allgäu)", 70000),
				new City("Hof", 46000),
				new City("Schweinfurt", 53000),
				new City("Straubing", 48000),
				new City("Neu-Ulm", 60000),
				new City("Memmingen", 46000),
				new City("Dachau", 48000),
				new City("Freising", 50000),
				new City("Kaufbeuren", 45000),
				new City("Ansbach", 42000),
				new City("Weiden in der Oberpfalz", 43000),
				new City("Lindau (Bodensee)", 26000),
				new City("Amberg", 42000),
				new City("Deggendorf", 38000),
				new City("Neuburg an der Donau", 30000),
				new City("Schwabach", 41000)
		);

		return bavariaCities;
	}
}



