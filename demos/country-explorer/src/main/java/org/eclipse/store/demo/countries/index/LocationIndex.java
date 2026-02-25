package org.eclipse.store.demo.countries.index;

import org.eclipse.store.demo.countries.model.Country;
import org.eclipse.store.gigamap.types.SpatialIndexer;

public class LocationIndex extends SpatialIndexer.Abstract<Country>
{
    @Override
    protected Double getLatitude(final Country country)
    {
        return country.latitude();
    }

    @Override
    protected Double getLongitude(final Country country)
    {
        return country.longitude();
    }
}
