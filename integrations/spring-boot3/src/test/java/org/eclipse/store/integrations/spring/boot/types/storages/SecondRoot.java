package org.eclipse.store.integrations.spring.boot.types.storages;

/*-
 * #%L
 * spring-boot3
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

public class SecondRoot
{
    private final Integer intValue = 50;
    private final char c = 'c';

    @Override
    public String toString()
    {
        return "SecondRoot{" +
                "intValue=" + intValue +
                ", c=" + c +
                '}';
    }

    public Integer getIntValue()
    {
        return intValue;
    }

    public char getC()
    {
        return c;
    }
}
