package test.eclipse.store.geo.data;

/*-
 * #%L
 * EclipseStore Integration Tests
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

public class City
{
    String name;
    long population;

    public City(String name, long population)
    {
        this.name = name;
        this.population = population;
    }

    @Override
    public String toString()
    {
        return "City{" +
                "name='" + name + '\'' +
                ", population=" + population +
                "}\n";
    }
}
