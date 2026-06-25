package test.eclipse.store.library.types;

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

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.eclipse.serializer.util.Substituter;
import org.junit.jupiter.api.Assertions;

public class SubstituterDefaultData implements BinaryHandlerTestData
{
    Substituter<String> value = Substituter.New();

    @Override
    public SubstituterDefaultData fillSampleData()
    {
        value.substitute("SomeString");
        return this;
    }

    @Override
    public void proveResults(Object o)
    {
        Assertions.assertNotNull(o);
        SubstituterDefaultData copy = (SubstituterDefaultData) o;
        assertEquals(this.value.toString(), copy.value.toString());
    }
}
