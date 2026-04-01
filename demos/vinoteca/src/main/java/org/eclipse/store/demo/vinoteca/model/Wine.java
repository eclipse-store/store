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

import javax.money.MonetaryAmount;

import org.eclipse.serializer.reference.Lazy;

public class Wine
{
	private String         name;
	private Winery         winery;
	private GrapeVariety   grapeVariety;
	private WineType       type;
	private int            vintage;
	private MonetaryAmount price;
	private double         rating;
	private int            ratingCount;
	private String         tastingNotes;
	private String         aroma;
	private String         foodPairing;
	private double         alcoholContent;
	private int            bottlesInStock;
	private boolean        available;
	private Lazy<List<Review>> reviews;

	public Wine()
	{
		this.reviews = Lazy.Reference(new ArrayList<>());
	}

	public Wine(
		final String         name,
		final Winery         winery,
		final GrapeVariety   grapeVariety,
		final WineType       type,
		final int            vintage,
		final MonetaryAmount price,
		final double         rating,
		final int            ratingCount,
		final String         tastingNotes,
		final String         aroma,
		final String         foodPairing,
		final double         alcoholContent,
		final int            bottlesInStock,
		final boolean        available
	)
	{
		this.name           = name;
		this.winery         = winery;
		this.grapeVariety   = grapeVariety;
		this.type           = type;
		this.vintage        = vintage;
		this.price          = price;
		this.rating         = rating;
		this.ratingCount    = ratingCount;
		this.tastingNotes   = tastingNotes;
		this.aroma          = aroma;
		this.foodPairing    = foodPairing;
		this.alcoholContent = alcoholContent;
		this.bottlesInStock = bottlesInStock;
		this.available      = available;
		this.reviews        = Lazy.Reference(new ArrayList<>());
	}

	public String getName()
	{
		return this.name;
	}

	public void setName(final String name)
	{
		this.name = name;
	}

	public Winery getWinery()
	{
		return this.winery;
	}

	public void setWinery(final Winery winery)
	{
		this.winery = winery;
	}

	public GrapeVariety getGrapeVariety()
	{
		return this.grapeVariety;
	}

	public void setGrapeVariety(final GrapeVariety grapeVariety)
	{
		this.grapeVariety = grapeVariety;
	}

	public WineType getType()
	{
		return this.type;
	}

	public void setType(final WineType type)
	{
		this.type = type;
	}

	public int getVintage()
	{
		return this.vintage;
	}

	public void setVintage(final int vintage)
	{
		this.vintage = vintage;
	}

	public MonetaryAmount getPrice()
	{
		return this.price;
	}

	public void setPrice(final MonetaryAmount price)
	{
		this.price = price;
	}

	public double getRating()
	{
		return this.rating;
	}

	public void setRating(final double rating)
	{
		this.rating = rating;
	}

	public int getRatingCount()
	{
		return this.ratingCount;
	}

	public void setRatingCount(final int ratingCount)
	{
		this.ratingCount = ratingCount;
	}

	public String getTastingNotes()
	{
		return this.tastingNotes;
	}

	public void setTastingNotes(final String tastingNotes)
	{
		this.tastingNotes = tastingNotes;
	}

	public String getAroma()
	{
		return this.aroma;
	}

	public void setAroma(final String aroma)
	{
		this.aroma = aroma;
	}

	public String getFoodPairing()
	{
		return this.foodPairing;
	}

	public void setFoodPairing(final String foodPairing)
	{
		this.foodPairing = foodPairing;
	}

	public double getAlcoholContent()
	{
		return this.alcoholContent;
	}

	public void setAlcoholContent(final double alcoholContent)
	{
		this.alcoholContent = alcoholContent;
	}

	public int getBottlesInStock()
	{
		return this.bottlesInStock;
	}

	public void setBottlesInStock(final int bottlesInStock)
	{
		this.bottlesInStock = bottlesInStock;
	}

	public boolean isAvailable()
	{
		return this.available;
	}

	public void setAvailable(final boolean available)
	{
		this.available = available;
	}

	public List<Review> getReviews()
	{
		return Lazy.get(this.reviews);
	}

	public Lazy<List<Review>> reviews()
	{
		return this.reviews;
	}

	public double getPriceAsDouble()
	{
		return this.price != null ? this.price.getNumber().doubleValue() : 0.0;
	}

	@Override
	public String toString()
	{
		return "Wine[" + this.name + ", " + this.vintage + ", " + this.type + "]";
	}
}
