package org.eclipse.store.gigamap.issues;

/*-
 * #%L
 * EclipseStore GigaMap
 * %%
 * Copyright (C) 2023 - 2025 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import org.eclipse.store.gigamap.types.BinaryIndexerInteger;
import org.eclipse.store.gigamap.types.BinaryIndexerString;
import org.eclipse.store.gigamap.types.GigaMap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class GigaMap500Test
{
    @Test
    void testRemove()
    {
        final var gigaMap = GigaMap.<User>Builder()
            .withBitmapUniqueIndex(ID_INDEXER)
            .withBitmapUniqueIndex(EMAIL_INDEXER)
            .build();

        final var user1 = new User(1, "john@doe.com");
        final var user2 = new User(2, "jane@doe.com");

        gigaMap.add(user1);
        gigaMap.add(user2);

        assertNotEquals(-1, gigaMap.remove(user1, ID_INDEXER));
        assertNotEquals(-1, gigaMap.remove(user2, EMAIL_INDEXER));
        assertTrue(gigaMap.isEmpty());
    }


    record User(int id, String email)
    {
    }

    private static final BinaryIndexerInteger<User> ID_INDEXER = new BinaryIndexerInteger.Abstract<>()
    {
        @Override
        protected Integer getInteger(final User user)
        {
            return user.id();
        }
    };

    private static final BinaryIndexerString<User> EMAIL_INDEXER = new BinaryIndexerString.Abstract<>()
    {
        @Override
        protected String getString(final User user)
        {
            return user.email();
        }
    };

}

