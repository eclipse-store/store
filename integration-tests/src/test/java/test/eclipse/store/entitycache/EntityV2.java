package test.eclipse.store.entitycache;

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

/**
 * "New" version of the evolving entity type ({@link EntityV1} is mapped to
 * this class via an explicit refactoring mapping). The matching {@code value}
 * field is carried over by legacy mapping; the additional field changes the
 * type structure, which is what forces a NEW typeId — with an identical
 * structure the type handler manager just re-assigns the old typeId to the
 * renamed class and no typeId change ever happens.
 */
public class EntityV2
{
    public String value;
    public long   extra;

    public EntityV2(final String value)
    {
        super();
        this.value = value;
    }
}
