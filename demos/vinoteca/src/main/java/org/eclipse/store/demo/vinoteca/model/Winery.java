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

/**
 * Domain entity representing a wine producer.
 * <p>
 * A {@code Winery} carries identifying attributes (name, region, country, founding year) and a
 * geographic location ({@link #getLatitude() latitude}/{@link #getLongitude() longitude}) that is
 * exploited by the spatial index registered on the wineries
 * {@link org.eclipse.store.gigamap.types.GigaMap GigaMap}; this enables the
 * {@code nearbyWineries} queries exposed via REST and GraphQL.
 * <p>
 * The list of wines produced by this winery is held behind a
 * {@link org.eclipse.serializer.reference.Lazy Lazy} reference so that the winery can be loaded
 * without pulling its entire wine collection into memory.
 *
 * @see Wine
 * @see org.eclipse.store.demo.vinoteca.index.WineryLocationIndex
 */
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

	/**
	 * No-arg constructor required by EclipseStore for object reconstruction during loading.
	 * Initializes an empty, lazily held wine list.
	 */
	public Winery()
	{
		this.wines = Lazy.Reference(new ArrayList<>());
	}

	/**
	 * Creates a fully populated {@code Winery} with an empty wine list.
	 *
	 * @param name        the winery name
	 * @param region      the wine-growing region (e.g. {@code "Tuscany"})
	 * @param country     the country in which the winery is located
	 * @param latitude    the geographic latitude in decimal degrees ([-90, 90])
	 * @param longitude   the geographic longitude in decimal degrees ([-180, 180])
	 * @param description a free-form description (may be {@code null})
	 * @param foundedYear the year the winery was founded
	 */
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

	/** @return the winery name */
	public String getName()
	{
		return this.name;
	}

	/** @param name the new winery name */
	public void setName(final String name)
	{
		this.name = name;
	}

	/** @return the wine-growing region */
	public String getRegion()
	{
		return this.region;
	}

	/** @param region the new region */
	public void setRegion(final String region)
	{
		this.region = region;
	}

	/** @return the country in which the winery is located */
	public String getCountry()
	{
		return this.country;
	}

	/** @param country the new country */
	public void setCountry(final String country)
	{
		this.country = country;
	}

	/** @return the geographic latitude in decimal degrees */
	public double getLatitude()
	{
		return this.latitude;
	}

	/** @param latitude the new latitude in decimal degrees */
	public void setLatitude(final double latitude)
	{
		this.latitude = latitude;
	}

	/** @return the geographic longitude in decimal degrees */
	public double getLongitude()
	{
		return this.longitude;
	}

	/** @param longitude the new longitude in decimal degrees */
	public void setLongitude(final double longitude)
	{
		this.longitude = longitude;
	}

	/** @return the free-form description, or {@code null} if none */
	public String getDescription()
	{
		return this.description;
	}

	/** @param description the new description (may be {@code null}) */
	public void setDescription(final String description)
	{
		this.description = description;
	}

	/** @return the year the winery was founded */
	public int getFoundedYear()
	{
		return this.foundedYear;
	}

	/** @param foundedYear the new founding year */
	public void setFoundedYear(final int foundedYear)
	{
		this.foundedYear = foundedYear;
	}

	/**
	 * Returns the wines produced by this winery, loading the lazy reference if necessary.
	 *
	 * @return the (possibly empty) wine list
	 */
	public List<Wine> getWines()
	{
		return Lazy.get(this.wines);
	}

	/**
	 * Returns the underlying lazy reference itself, useful when callers want to control loading
	 * (for example to call {@link Lazy#clear()}) instead of forcing the wrapped value to load.
	 *
	 * @return the lazy reference holding the wine list
	 */
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
