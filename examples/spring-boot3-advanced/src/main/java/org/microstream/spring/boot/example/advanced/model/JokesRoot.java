package org.microstream.spring.boot.example.advanced.model;

/*-
 * #%L
 * spring-boot3-simple
 * %%
 * Copyright (C) 2023 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import java.util.ArrayList;
import java.util.List;

public class JokesRoot
{
    private List<String> jokes = new ArrayList<>();

    public List<String> getJokes()
    {
        return jokes;
    }

    public void setJokes(List<String> jokes)
    {
        this.jokes = jokes;
    }

}
