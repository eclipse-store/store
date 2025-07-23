package org.eclipse.store.gigamap.indexer.annotation.binary;

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

public class CarBinary
{
    @Unique
    @Index
    public String vin;

    @Index
    public String make;

    @Index(binary = true)
    public Long vehicleId;

    public CarBinary(String vin, String make, Long vehicleId)
    {
        this.vin = vin;
        this.make = make;
        this.vehicleId = vehicleId;
    }

    public String getVin()
    {
        return vin;
    }

    public String getMake()
    {
        return make;
    }

    public Long getVehicleId()
    {
        return vehicleId;
    }

    @Override
    public String toString()
    {
        return "CarBinary{" +
                "vin='" + vin + '\'' +
                ", make='" + make + '\'' +
                ", vehicleId=" + vehicleId +
                '}';
    }
}
