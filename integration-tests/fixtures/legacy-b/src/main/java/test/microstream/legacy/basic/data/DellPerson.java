package test.microstream.legacy.basic.data;

/*-
 * #%L
 * EclipseStore Integration Tests - Fixtures - Legacy B
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

public class DellPerson {

    private static final String HAIR_COLOR = "hair_color";

    private String firstName;
    private String fullName;
    private Map<String, String> attributes = new HashMap<>();
    private int age = 2;

    public DellPerson() {
    }

    public DellPerson(String firstName, String hairColor, String fullName) {
        this.firstName = firstName;
        this.attributes.put(HAIR_COLOR, hairColor);
        this.fullName = fullName;
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }

    public String findHairColor() {
        return attributes.get(HAIR_COLOR);
    }

    public void setHairColor(String color) {
        attributes.put(HAIR_COLOR, color);
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public Map<String, String> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, String> attributes) {
        this.attributes = attributes;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    @Override
    public String toString() {
        return "DellPerson{" +
                "firstName='" + firstName + '\'' +
                ", fullName='" + fullName + '\'' +
                ", attributes=" + attributes +
                ", age=" + age +
                '}';
    }
}
