
package org.eclipse.store.integrations.cdi.types.extension;

/*-
 * #%L
 * EclipseStore Integrations CDI 4
 * %%
 * Copyright (C) 2023 - 2024 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */





import org.eclipse.serializer.exceptions.NoSuchMethodRuntimeException;
import org.eclipse.serializer.reflect.XReflect;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;


class ConstructorUtilTest
{
    @Test
    @DisplayName("Should return NPE when it uses")
    public void shouldReturnNPEWhenThereIsNull()
    {
        Assertions.assertThrows(NullPointerException.class, () -> XReflect.defaultInstantiate(null));
    }

    @Test
    public void shouldReturnErrorWhenThereIsInterface()
    {
        Assertions.assertThrows(NoSuchMethodRuntimeException.class, () -> XReflect.defaultInstantiate(Animal.class));
    }

    @Test
    public void shouldReturnErrorWhenThereNoDefaultConstructor()
    {
        Assertions.assertThrows(NoSuchMethodRuntimeException.class, () -> XReflect.defaultInstantiate(Lion.class));
    }

    @Test
    public void shouldReturnConstructor()
    {
        final Tiger tiger = XReflect.defaultInstantiate(Tiger.class);
        Assertions.assertNotNull(tiger);
    }

    @Test
    public void shouldCreateDefaultConstructor()
    {
        final Cat cat = XReflect.defaultInstantiate(Cat.class);
        Assertions.assertNotNull(cat);
    }
}
