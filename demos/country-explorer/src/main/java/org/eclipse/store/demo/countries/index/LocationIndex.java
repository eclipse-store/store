package org.eclipse.store.demo.countries.index;

import org.eclipse.store.demo.countries.model.Country;
import org.eclipse.store.gigamap.types.SpatialIndexer;

/**
 * Spatial indexer that maps {@link Country} entities to their geographic coordinates.
 * <p>
 * This class extends {@link SpatialIndexer.Abstract} and extracts the latitude and
 * longitude from a {@code Country} record so that the
 * {@link org.eclipse.store.gigamap.types.GigaMap GigaMap} can perform spatial queries
 * such as:
 * <ul>
 *   <li><strong>Radius search</strong> &ndash; find countries within a given distance
 *       of a point ({@link #near(double, double, double)}).</li>
 *   <li><strong>Bounding-box search</strong> &ndash; find countries inside a rectangular
 *       area ({@link #withinBox(double, double, double, double)}).</li>
 *   <li><strong>Hemisphere / latitude band</strong> &ndash; filter by latitude or
 *       longitude thresholds ({@link #latitudeAbove(double)},
 *       {@link #latitudeBetween(double, double)}, etc.).</li>
 * </ul>
 *
 * <p>A single shared instance is created in {@link CountryIndices#LOCATION} and
 * registered with the {@code GigaMap} during application startup.
 *
 * @see CountryIndices
 * @see SpatialIndexer
 */
public class LocationIndex extends SpatialIndexer.Abstract<Country>
{
    /**
     * Extracts the latitude from the given country.
     *
     * @param country the country whose latitude is requested
     * @return the latitude of the country's capital in decimal degrees
     */
    @Override
    protected Double getLatitude(final Country country)
    {
        return country.latitude();
    }

    /**
     * Extracts the longitude from the given country.
     *
     * @param country the country whose longitude is requested
     * @return the longitude of the country's capital in decimal degrees
     */
    @Override
    protected Double getLongitude(final Country country)
    {
        return country.longitude();
    }
}
