package test.eclipse.store.geo.data;

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

import java.util.List;

public class State
{
	String name;
	String capital;
	long   population;
	List<City> cities;

	public State(String name, String capital, long population, List<City> cities)
	{
		this.name = name;
		this.capital = capital;
		this.population = population;
		this.cities = cities;
	}

	public String getName()
	{
		return name;
	}

	public String getCapital()
	{
		return capital;
	}

	public long getPopulation()
	{
		return population;
	}

	public List<City> getCities()
	{
		return cities;
	}

	@Override
	public String toString()
	{
		return "State{" +
				"name='" + name + '\'' +
				", capital='" + capital + '\'' +
				", population=" + population +
				", cities=" + cities +
				"}\n:";
	}
}
