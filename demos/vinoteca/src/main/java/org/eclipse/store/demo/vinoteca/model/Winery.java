package org.eclipse.store.demo.vinoteca.model;

/*-
 * #%L
 * EclipseStore Demo Vinoteca
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
import java.util.List;

import org.eclipse.serializer.reference.Lazy;

public class Winery
{
	private String     name;
	private String     region;
	private String     country;
	private double     latitude;
	private double     longitude;
	private String     description;
	private int        foundedYear;
	private Lazy<List<Wine>> wines;

	public Winery()
	{
		this.wines = Lazy.Reference(new ArrayList<>());
	}

	public Winery(
		final String name,
		final String region,
		final String country,
		final double latitude,
		final double longitude,
		final String description,
		final int    foundedYear
	)
	{
		this.name        = name;
		this.region      = region;
		this.country     = country;
		this.latitude    = latitude;
		this.longitude   = longitude;
		this.description = description;
		this.foundedYear = foundedYear;
		this.wines       = Lazy.Reference(new ArrayList<>());
	}

	public String getName()
	{
		return this.name;
	}

	public void setName(final String name)
	{
		this.name = name;
	}

	public String getRegion()
	{
		return this.region;
	}

	public void setRegion(final String region)
	{
		this.region = region;
	}

	public String getCountry()
	{
		return this.country;
	}

	public void setCountry(final String country)
	{
		this.country = country;
	}

	public double getLatitude()
	{
		return this.latitude;
	}

	public void setLatitude(final double latitude)
	{
		this.latitude = latitude;
	}

	public double getLongitude()
	{
		return this.longitude;
	}

	public void setLongitude(final double longitude)
	{
		this.longitude = longitude;
	}

	public String getDescription()
	{
		return this.description;
	}

	public void setDescription(final String description)
	{
		this.description = description;
	}

	public int getFoundedYear()
	{
		return this.foundedYear;
	}

	public void setFoundedYear(final int foundedYear)
	{
		this.foundedYear = foundedYear;
	}

	public List<Wine> getWines()
	{
		return Lazy.get(this.wines);
	}

	public Lazy<List<Wine>> wines()
	{
		return this.wines;
	}

	@Override
	public String toString()
	{
		return "Winery[" + this.name + ", " + this.region + ", " + this.country + "]";
	}
}
