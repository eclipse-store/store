package one.microstream.test.storingdata;

/*-
 * #%L
 * EclipseStore Integration Tests - Skills - storing-data
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

public class Customer
{
    private String  email;
    private String  name;
    private int     orderCount;
    private Address address;

    public Customer()
    {
        // no-arg ctor
    }

    public Customer(final String email, final String name, final Address address)
    {
        this.email      = email;
        this.name       = name;
        this.orderCount = 0;
        this.address    = address;
    }

    public String  email()      { return this.email;      }
    public String  name()       { return this.name;       }
    public int     orderCount() { return this.orderCount; }
    public Address address()    { return this.address;    }

    public void setEmail(final String email)         { this.email      = email;   }
    public void setOrderCount(final int orderCount)  { this.orderCount = orderCount; }
    public void setAddress(final Address address)    { this.address    = address; }

    public void incrementOrderCount()
    {
        this.orderCount++;
    }
}
