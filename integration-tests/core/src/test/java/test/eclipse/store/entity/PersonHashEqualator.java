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

import org.eclipse.serializer.hashing.HashEqualator;
import org.eclipse.serializer.typing.Stateless;

public interface PersonHashEqualator extends HashEqualator<Person>
{
    public static PersonHashEqualator New()
    {
        return new Default();
    }

    public final class Default implements PersonHashEqualator, Stateless
    {
        public static boolean equals(final Person person1, final Person person2)
        {
            return Objects.equals(person1.firstName(), person2.firstName())
                    && Objects.equals(person1.lastName(), person2.lastName());
        }

        public static int hashCode(final Person person)
        {
            return Objects.hash(person.firstName(), person.lastName());
        }

        Default()
        {
            super();
        }

        @Override
        public boolean equal(final Person person1, final Person person2)
        {
            return equals(person1, person2);
        }

        @Override
        public int hash(final Person person)
        {
            return hashCode(person);

        }

    }

}
