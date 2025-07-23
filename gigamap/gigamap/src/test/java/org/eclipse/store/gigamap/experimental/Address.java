package org.eclipse.store.gigamap.experimental;

/*-
 * #%L
 * EclipseStore GigaMap
 * %%
 * Copyright (C) 2023 - 2025 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

public class Address
{
    private  String street;
    private  String city;
    private  String zip;

    public Address(final String street, final String city, final String zip)
    {
        super();
        this.street = street;
        this.city   = city;
        this.zip    = zip;
    }

    public String street()
    {
        return this.street;
    }

    public String city()
    {
        return this.city;
    }

    public String zip()
    {
        return this.zip;
    }

    @Override
    public String toString()
    {
        return "Address [street=" + this.street + ", city=" + this.city + ", zip=" + this.zip + "]";
    }


    public String getStreet()
    {
        return street;
    }

    public void setStreet(String street)
    {
        this.street = street;
    }

    public String getCity()
    {
        return city;
    }

    public void setCity(String city)
    {
        this.city = city;
    }

    public String getZip()
    {
        return zip;
    }

    public void setZip(String zip)
    {
        this.zip = zip;
    }
}
