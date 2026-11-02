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

public class CsvPerson
{

    private static final String HAIR_COLOR = "hair_color";
    private static final String EYE_COLOR = "eye_color";

    private String firstName;
    private String lastName;
    private String original = "original";
    private Map<String, String> attributes = new HashMap<>();
    private Integer age = null;

    public CsvPerson(String firstName, String lastName, String eyeColor, String hairColor)
    {
        this.firstName = firstName;
        this.lastName = lastName;
        this.attributes.put(HAIR_COLOR, hairColor);
        this.attributes.put(EYE_COLOR, eyeColor);
    }

    public CsvPerson()
    {

    }

    public String getOriginal()
    {
        return original;
    }

    public void setOriginal(String original)
    {
        this.original = original;
    }

    public Integer getAge()
    {
        return age;
    }

    public void setAge(Integer age)
    {
        this.age = age;
    }

    public String findHairColor()
    {
        return attributes.get(HAIR_COLOR);
    }

    public String findEyeColor()
    {
        return attributes.get(EYE_COLOR);
    }

    public void setEyeColor(String color)
    {
        attributes.put(EYE_COLOR, color);
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
