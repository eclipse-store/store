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
 * "Old" version of the evolving entity type. Only used to seed the storage;
 * the second session maps this type name to {@link EntityV2}.
 */
public class EntityV1
{
    public String value;

    public EntityV1(final String value)
    {
        super();
        this.value = value;
    }
}
