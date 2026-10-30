package test.eclipse.store.legacy.legacy.reference.data;

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

public class ReferencePerson2
{

    private String firstName;
    private String secondName;
    private String userName;
    private ReferenceAddress address2;

    public ReferencePerson2()
    {
    }

    public ReferencePerson2(String firstName, String secondName, String userName, ReferenceAddress address)
    {
        this.firstName = firstName;
        this.secondName = secondName;
        this.userName = userName;
        this.address2 = address;
    }

    public String getFirstName()
    {
        return firstName;
    }

    public void setFirstName(String firstName)
    {
        this.firstName = firstName;
    }

    public String getSecondName()
    {
        return secondName;
    }

    public void setSecondName(String secondName)
    {
        this.secondName = secondName;
    }

    public String getUserName()
    {
        return userName;
    }

    public void setUserName(String userName)
    {
        this.userName = userName;
    }

    public ReferenceAddress getAddress2()
    {
        return address2;
    }

    public void setAddress2(ReferenceAddress address2)
    {
        this.address2 = address2;
    }
}
