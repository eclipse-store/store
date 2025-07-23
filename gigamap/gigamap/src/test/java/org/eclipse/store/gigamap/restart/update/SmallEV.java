package org.eclipse.store.gigamap.restart.update;

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

public class SmallEV
{
    private String vin;
    private String make;
    private String model;
    private int year;
    private Boolean isElectric;

    public SmallEV(String vin)
    {
        this.vin = vin;
    }

    public String getVin()
    {
        return vin;
    }

    public String getMake()
    {
        return make;
    }

    public String getModel()
    {
        return model;
    }

    public int getYear()
    {
        return year;
    }

    public void setVin(String vin)
    {
        this.vin = vin;
    }

    public void setMake(String make)
    {
        this.make = make;
    }

    public void setModel(String model)
    {
        this.model = model;
    }

    public void setYear(int year)
    {
        this.year = year;
    }

    public Boolean getElectric()
    {
        return isElectric;
    }

    public void setElectric(Boolean electric)
    {
        isElectric = electric;
    }
}
