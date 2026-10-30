package test.eclipse.store.fixtures;

/*-
 * #%L
 * EclipseStore Integration Tests - Core
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

import org.junit.jupiter.api.Test;
import test.microstream.library.TypeEnum;

import static org.junit.jupiter.api.Assertions.assertSame;

public class TypeEnumTest {

    @Test
    public void get_enum_by_value() {
        Object o = TypeEnum.valueOf("PrimitiveTypes");

        assertSame(o, TypeEnum.PrimitiveTypes);
    }
}
