package test.eclipse.store.entity;

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

import java.util.Objects;

import org.eclipse.serializer.entity.EntityData;

public class PersonData extends EntityData implements Person
{
    private final String firstName;
    private final String lastName;

    protected PersonData(final Person entity, final String firstName, final String lastName)
    {
        super(entity);
        this.firstName = firstName;
        this.lastName = lastName;
    }

    @Override
    public String firstName()
    {
        return this.firstName;
    }

    @Override
    public String lastName()
    {
        return this.lastName;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PersonData that = (PersonData) o;
        return Objects.equals(firstName, that.firstName) &&
                Objects.equals(lastName, that.lastName);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(firstName, lastName);
    }
}
