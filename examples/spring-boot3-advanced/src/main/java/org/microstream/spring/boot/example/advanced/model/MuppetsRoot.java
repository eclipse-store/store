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

public class MuppetsRoot
{
    private List<String> muppets = new ArrayList<>();

    public List<String> getMuppets()
    {
        return muppets;
    }

    public void setMuppets(List<String> muppets)
    {
        this.muppets = muppets;
    }
}
