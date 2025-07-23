package org.eclipse.store.gigamap.indexer.inheritance;

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

public class Person
{
    private int Age;
    private String Name;

    public Person(int age, String name)
    {
        Age = age;
        Name = name;
    }

    public int getAge()
    {
        return Age;
    }

    public String getName()
    {
        return Name;
    }

    public void setAge(int age)
    {
        Age = age;
    }

    public void setName(String name)
    {
        Name = name;
    }

    @Override
    public String toString()
    {
        return "Person{" +
                "Age=" + Age +
                ", Name='" + Name + '\'' +
                '}';
    }
}
