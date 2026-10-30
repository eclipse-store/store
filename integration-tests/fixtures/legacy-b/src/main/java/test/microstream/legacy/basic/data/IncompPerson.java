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

public class IncompPerson {

    private String firstName;
    private Integer secondName;
    private String userName;

    public IncompPerson() {
    }

    public IncompPerson(String firstName, Integer secondName, String userName) {
        this.firstName = firstName;
        this.secondName = secondName;
        this.userName = userName;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public Integer getSecondName() {
        return secondName;
    }

    public void setSecondName(Integer secondName) {
        this.secondName = secondName;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    @Override
    public String toString() {
        return "IncompPerson{" +
                "firstName='" + firstName + '\'' +
                ", secondName=" + secondName +
                ", userName='" + userName + '\'' +
                '}';
    }
}
