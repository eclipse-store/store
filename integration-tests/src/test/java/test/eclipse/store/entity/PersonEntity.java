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

import org.eclipse.serializer.entity.EntityLayerIdentity;

public class PersonEntity extends EntityLayerIdentity implements Person
{
    protected PersonEntity()
    {
        super();
    }

    @Override
    protected Person entityData()
    {
        return (Person) super.entityData();
    }

    @Override
    public final String firstName()
    {
        return this.entityData().firstName();
    }

    @Override
    public final String lastName()
    {
        return this.entityData().lastName();
    }
}
