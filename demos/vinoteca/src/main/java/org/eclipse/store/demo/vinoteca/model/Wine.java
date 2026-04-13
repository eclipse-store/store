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

/**
 * Domain entity representing a single wine in the Vinoteca catalog.
 * <p>
 * A {@code Wine} carries the product attributes used both for catalog presentation (name, vintage,
 * tasting notes, aroma, food pairing, alcohol content) and for indexing inside the
 * {@link org.eclipse.store.gigamap.types.GigaMap GigaMap} of the application's {@link DataRoot}.
 * The associated {@link #getReviews() reviews} are held behind an
 * {@link org.eclipse.serializer.reference.Lazy Lazy} reference so that EclipseStore can
 * load them on demand instead of pulling the whole list into memory eagerly.
 * <p>
 * Wines are referenced (not duplicated) by {@link Winery#getWines()} and by
 * {@link OrderItem#getWine()}, so a single wine instance lives once in the object graph and
 * participates in multiple relationships.
 *
 * @see Winery
 * @see Review
 * @see WineType
 * @see GrapeVariety
 */
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

	/**
	 * No-arg constructor required by EclipseStore for object reconstruction during loading.
	 * Initializes an empty, lazily held review list.
	 */
	public Wine()
	{
		this.reviews = Lazy.Reference(new ArrayList<>());
	}

	/**
	 * Creates a fully populated {@code Wine} with an empty review list.
	 *
	 * @param name           the commercial name of the wine
	 * @param winery         the producing {@link Winery} (must not be {@code null} for indexing)
	 * @param grapeVariety   the dominant {@link GrapeVariety}
	 * @param type           the {@link WineType} (red, white, rose, …)
	 * @param vintage        the year of harvest
	 * @param price          the bottle price as a {@link MonetaryAmount}
	 * @param rating         the current average customer rating (0.0 – 5.0)
	 * @param ratingCount    the number of ratings that contributed to {@code rating}
	 * @param tastingNotes   free-form tasting notes (indexed for full-text search)
	 * @param aroma          aroma description (indexed for full-text search)
	 * @param foodPairing    suggested food pairing (indexed for full-text search)
	 * @param alcoholContent alcohol content in percent by volume
	 * @param bottlesInStock current bottle inventory
	 * @param available      whether the wine is currently offered for sale
	 */
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

	/** @return the commercial name of the wine */
	public String getName()
	{
		return this.name;
	}

	/** @param name the new commercial name */
	public void setName(final String name)
	{
		this.name = name;
	}

	/** @return the producing winery */
	public Winery getWinery()
	{
		return this.winery;
	}

	/** @param winery the new producing winery */
	public void setWinery(final Winery winery)
	{
		this.winery = winery;
	}

	/** @return the dominant grape variety */
	public GrapeVariety getGrapeVariety()
	{
		return this.grapeVariety;
	}

	/** @param grapeVariety the new grape variety */
	public void setGrapeVariety(final GrapeVariety grapeVariety)
	{
		this.grapeVariety = grapeVariety;
	}

	/** @return the wine type (red, white, rose, …) */
	public WineType getType()
	{
		return this.type;
	}

	/** @param type the new wine type */
	public void setType(final WineType type)
	{
		this.type = type;
	}

	/** @return the year of harvest */
	public int getVintage()
	{
		return this.vintage;
	}

	/** @param vintage the new vintage year */
	public void setVintage(final int vintage)
	{
		this.vintage = vintage;
	}

	/** @return the bottle price as a {@link MonetaryAmount} */
	public MonetaryAmount getPrice()
	{
		return this.price;
	}

	/** @param price the new bottle price */
	public void setPrice(final MonetaryAmount price)
	{
		this.price = price;
	}

	/** @return the average customer rating (0.0 – 5.0) */
	public double getRating()
	{
		return this.rating;
	}

	/** @param rating the new average rating */
	public void setRating(final double rating)
	{
		this.rating = rating;
	}

	/** @return the number of ratings that contributed to {@link #getRating()} */
	public int getRatingCount()
	{
		return this.ratingCount;
	}

	/** @param ratingCount the new rating count */
	public void setRatingCount(final int ratingCount)
	{
		this.ratingCount = ratingCount;
	}

	/** @return the free-form tasting notes (indexed for full-text search) */
	public String getTastingNotes()
	{
		return this.tastingNotes;
	}

	/** @param tastingNotes the new tasting notes */
	public void setTastingNotes(final String tastingNotes)
	{
		this.tastingNotes = tastingNotes;
	}

	/** @return the aroma description (indexed for full-text search) */
	public String getAroma()
	{
		return this.aroma;
	}

	/** @param aroma the new aroma description */
	public void setAroma(final String aroma)
	{
		this.aroma = aroma;
	}

	/** @return the suggested food pairing (indexed for full-text search) */
	public String getFoodPairing()
	{
		return this.foodPairing;
	}

	/** @param foodPairing the new food pairing description */
	public void setFoodPairing(final String foodPairing)
	{
		this.foodPairing = foodPairing;
	}

	/** @return the alcohol content in percent by volume */
	public double getAlcoholContent()
	{
		return this.alcoholContent;
	}

	/** @param alcoholContent the new alcohol content (% vol.) */
	public void setAlcoholContent(final double alcoholContent)
	{
		this.alcoholContent = alcoholContent;
	}

	/** @return the current bottle inventory */
	public int getBottlesInStock()
	{
		return this.bottlesInStock;
	}

	/** @param bottlesInStock the new bottle inventory */
	public void setBottlesInStock(final int bottlesInStock)
	{
		this.bottlesInStock = bottlesInStock;
	}

	/** @return {@code true} if the wine is currently offered for sale */
	public boolean isAvailable()
	{
		return this.available;
	}

	/** @param available the new availability flag */
	public void setAvailable(final boolean available)
	{
		this.available = available;
	}

	/**
	 * Returns the list of customer reviews, loading the lazy reference if necessary.
	 *
	 * @return the (possibly empty) review list
	 */
	public List<Review> getReviews()
	{
		return Lazy.get(this.reviews);
	}

	/**
	 * Returns the underlying lazy reference itself, useful when callers want to control loading
	 * (for example to call {@link Lazy#clear()}) instead of forcing the wrapped value to load.
	 *
	 * @return the lazy reference holding the review list
	 */
	public Lazy<List<Review>> reviews()
	{
		return this.reviews;
	}

	/**
	 * Convenience accessor that flattens the {@link MonetaryAmount} price to a plain {@code double},
	 * primarily used by the Vaadin grid columns and analytics computations.
	 *
	 * @return the price as a {@code double}, or {@code 0.0} if no price is set
	 */
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
