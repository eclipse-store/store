package one.microstream.test.customtypehandlers;

/*-
 * #%L
 * EclipseStore Integration Tests - Skills - custom-type-handlers
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

import java.util.Objects;

public final class HostEntry
{
    private final String    hostname;
    private final IpAddress address;

    public HostEntry(final String hostname, final IpAddress address)
    {
        this.hostname = hostname;
        this.address  = address;
    }

    public String hostname()
    {
        return this.hostname;
    }

    public IpAddress address()
    {
        return this.address;
    }

    @Override
    public boolean equals(final Object o)
    {
        return o instanceof HostEntry other
            && Objects.equals(this.hostname, other.hostname)
            && Objects.equals(this.address,  other.address);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(this.hostname, this.address);
    }

    @Override
    public String toString()
    {
        return "HostEntry(" + this.hostname + " -> " + this.address + ")";
    }
}
