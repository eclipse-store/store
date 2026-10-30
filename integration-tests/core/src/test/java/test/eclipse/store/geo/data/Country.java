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

public class Country
{
    String name;
    List<State> states;

    public Country(String name, List<State> states)
    {
        this.name = name;
        this.states = states;
    }

    public String getName()
    {
        return name;
    }

    public List<State> getStates()
    {
        return states;
    }

    @Override
    public String toString()
    {
        return "Country{" +
                "name='" + name + '\'' +
                ", states=" + states +
                "}\n:";
    }
}
