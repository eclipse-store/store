package test.microstream.legacy.basic.data;

/*-
 * #%L
 * EclipseStore Integration Tests - Fixtures - Legacy A
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

public class Person  {

    private String userCode;
    private String firstName;
    private String secondName;

    public Person() {
    }

    public Person(String firstName, String secondName, String userCode) {
        this.firstName = firstName;
        this.secondName = secondName;
        this.userCode = userCode;
    }

    public static Person createSample() {
        return new Person("Karel", "May", "1001");
    }

    public static Person createEmpty() {
        return new Person();
    }

    public String getUserCode() {
        return userCode;
    }

    public void setUserCode(String userCode) {
        this.userCode = userCode;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getSecondName() {
        return secondName;
    }

    public void setSecondName(String secondName) {
        this.secondName = secondName;
    }

    @Override
    public String toString() {
        return "Person{" +
                "userCode='" + userCode + '\'' +
                ", firstName='" + firstName + '\'' +
                ", secondName='" + secondName + '\'' +
                '}';
    }
}
