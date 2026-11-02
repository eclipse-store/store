package test.eclipse.store.legacy.csv.data;

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

import java.util.HashMap;
import java.util.Map;

public class CsvPerson2
{

    private static final String HAIR_COLOR = "hair_color";

    private String tittle;
    private String firstName;
    private String lastName;
    private String copy = "copy";
    private Map<String, String> attributes = new HashMap<>();
    private int age = 2;

    public CsvPerson2()
    {
    }

    public CsvPerson2(String tittle, String firstName, String lastName, String hairColor)
    {
        this.tittle = tittle;
        this.firstName = firstName;
        this.lastName = lastName;
        this.attributes.put(HAIR_COLOR, hairColor);
    }

    public String getTittle()
    {
        return tittle;
    }

    public String getCopy()
    {
        return copy;
    }

    public void setCopy(String copy)
    {
        this.copy = copy;
    }

    public int getAge()
    {
        return age;
    }

    public void setAge(int age)
    {
        this.age = age;
    }

    public String findHairColor()
    {
        return attributes.get(HAIR_COLOR);
    }

    public void setHairColor(String color)
    {
        attributes.put(HAIR_COLOR, color);
    }

    public String getFirstName()
    {
        return firstName;
    }

    public void setFirstName(String firstName)
    {
        this.firstName = firstName;
    }

    public String getLastName()
    {
        return lastName;
    }

    public void setLastName(String lastName)
    {
        this.lastName = lastName;
    }

    public Map<String, String> getAttributes()
    {
        return attributes;
    }

    public void setAttributes(Map<String, String> attributes)
    {
        this.attributes = attributes;
    }
}
