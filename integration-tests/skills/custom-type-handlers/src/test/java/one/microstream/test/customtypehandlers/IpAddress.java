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

public final class IpAddress
{
    private final int packed;

    public IpAddress(final int packed)
    {
        this.packed = packed;
    }

    public int packed()
    {
        return this.packed;
    }

    public String toDotted()
    {
        return ((this.packed >>> 24) & 0xFF) + "."
             + ((this.packed >>> 16) & 0xFF) + "."
             + ((this.packed >>>  8) & 0xFF) + "."
             + ( this.packed         & 0xFF);
    }

    @Override
    public boolean equals(final Object o)
    {
        return o instanceof IpAddress other && other.packed == this.packed;
    }

    @Override
    public int hashCode()
    {
        return Integer.hashCode(this.packed);
    }

    @Override
    public String toString()
    {
        return "IpAddress(" + this.toDotted() + ")";
    }
}
