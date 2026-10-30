package one.microstream.test.gigamap.travel;

/*-
 * #%L
 * EclipseStore Integration Tests - Skills - gigamap
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

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public abstract class Accommodation
{
    public enum Type { HOTEL, CAMP, APARTMENT }

    private final UUID id;
    private String name;
    private String country;
    private String city;
    private double latitude;
    private double longitude;
    private double pricePerNight;
    private String description;
    private final Set<String> amenities;
    private float[] featureVector;

    protected Accommodation(
        UUID id, String name, String country, String city,
        double latitude, double longitude,
        double pricePerNight, String description,
        Set<String> amenities, float[] featureVector)
    {
        this.id              = id;
        this.name            = name;
        this.country         = country;
        this.city            = city;
        this.latitude        = latitude;
        this.longitude       = longitude;
        this.pricePerNight   = pricePerNight;
        this.description     = description;
        this.amenities       = new LinkedHashSet<>(amenities);
        this.featureVector   = featureVector;
    }

    public abstract Type type();

    public UUID    id()            { return id; }
    public String  name()          { return name; }
    public String  country()       { return country; }
    public String  city()          { return city; }
    public Double  latitude()      { return latitude; }
    public Double  longitude()     { return longitude; }
    public double  pricePerNight() { return pricePerNight; }
    public String  description()   { return description; }
    public Set<String> amenities() { return amenities; }
    public float[] featureVector() { return featureVector; }

    public void setName(String name)                   { this.name = name; }
    public void setCity(String city)                   { this.city = city; }
    public void setCountry(String country)             { this.country = country; }
    public void setPricePerNight(double p)             { this.pricePerNight = p; }
    public void setDescription(String description)     { this.description = description; }
    public void setLatitude(double latitude)           { this.latitude = latitude; }
    public void setLongitude(double longitude)         { this.longitude = longitude; }
    public void setFeatureVector(float[] v)            { this.featureVector = v; }

    @Override
    public String toString()
    {
        return type() + "[" + name + ", " + city + ", " + country
            + ", " + pricePerNight + "/night]";
    }
}
