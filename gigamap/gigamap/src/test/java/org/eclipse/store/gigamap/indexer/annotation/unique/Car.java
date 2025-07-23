package org.eclipse.store.gigamap.indexer.annotation.unique;

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

import org.eclipse.store.gigamap.annotations.Index;
import org.eclipse.store.gigamap.annotations.Unique;

import java.util.Objects;


public class Car
{
    @Unique
    @Index
    public String vin;

    public String make;

    public Car(String vin, String make)
    {
        this.vin = vin;
        this.make = make;
    }

    public String getVin()
    {
        return vin;
    }

    public void setVin(String vin)
    {
        this.vin = vin;
    }

    public String getMake()
    {
        return make;
    }

    public void setMake(String make)
    {
        this.make = make;
    }

    @Override
    public String toString()
    {
        return "Car{" +
                "vin='" + vin + '\'' +
                ", make='" + make + '\'' +
                '}';
    }
    
    @Override
    public boolean equals(final Object o)
    {
        if(!(o instanceof Car))
            return false;
        final Car car = (Car)o;
        return Objects.equals(this.vin, car.vin);
    }
    
    @Override
    public int hashCode()
    {
        return Objects.hashCode(this.vin);
    }
    
}
