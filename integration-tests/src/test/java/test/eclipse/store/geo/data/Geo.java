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

import java.util.List;

public class Geo
{
    String name;
    List<Country> countries;

    public Geo(String name)
    {
        this.name = name;
    }

    public Geo()
    {
    }

    public Geo(String name, List<Country> countries)
    {
        this.name = name;
        this.countries = countries;
    }

    public Geo(List<Country> countries)
    {
        this.countries = countries;
    }

    public List<Country> getCountries()
    {
        return countries;
    }

    @Override
    public String toString()
    {
        return "Geo{" +
                "states=" + countries +
                "}\n:";
    }
}
