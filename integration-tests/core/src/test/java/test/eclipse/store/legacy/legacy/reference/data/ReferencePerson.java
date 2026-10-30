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

public class ReferencePerson
{

    private String userCode;
    private String firstName;
    private String secondName;
    private ReferenceAddress adrress;

    public ReferencePerson()
    {
    }

    public ReferencePerson(String firstName, String secondName, String userCode, ReferenceAddress address)
    {
        this.firstName = firstName;
        this.secondName = secondName;
        this.userCode = userCode;
        this.adrress = address;
    }

    public String getUserCode()
    {
        return userCode;
    }

    public void setUserCode(String userCode)
    {
        this.userCode = userCode;
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

    public ReferenceAddress getAdrress()
    {
        return adrress;
    }

    public void setAdrress(ReferenceAddress adrress)
    {
        this.adrress = adrress;
    }
}
