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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.eclipse.serializer.persistence.types.BatchStorer;
import org.eclipse.serializer.persistence.types.Storer;
import org.junit.jupiter.api.Test;

/**
 * Pitfall #7 — {@link Storer} is NOT {@link AutoCloseable}; {@link BatchStorer} IS.
 */
class PatternEStorerNotAutoCloseableTest
{
    @Test
    void storer_is_not_autocloseable()
    {
        assertFalse(AutoCloseable.class.isAssignableFrom(Storer.class),
            "Storer must NOT implement AutoCloseable — manual commit() is required.");
    }

    @Test
    void batch_storer_is_autocloseable()
    {
        assertTrue(AutoCloseable.class.isAssignableFrom(BatchStorer.class),
            "BatchStorer must implement AutoCloseable — try-with-resources is the idiom.");
    }
}
