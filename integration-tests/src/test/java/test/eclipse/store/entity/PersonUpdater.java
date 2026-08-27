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


import org.eclipse.serializer.entity.Entity;

public interface PersonUpdater extends Entity.Updater<Person, PersonUpdater>
{
    public static boolean setFirstName(final Person person, final String firstName)
    {
        return New(person).firstName(firstName).update();
    }

    public static boolean setLastName(final Person person, final String lastName)
    {
        return New(person).lastName(lastName).update();
    }

    public PersonUpdater firstName(String firstName);

    public PersonUpdater lastName(String lastName);

    public static PersonUpdater New(final Person person)
    {
        return new Default(person);
    }

    public class Default
            extends Entity.Updater.Abstract<Person, PersonUpdater>
            implements PersonUpdater
    {
        private String firstName;
        private String lastName;

        protected Default(final Person person)
        {
            super(person);
        }

        @Override
        public PersonUpdater firstName(final String firstName)
        {
            this.firstName = firstName;
            return this;
        }

        @Override
        public PersonUpdater lastName(final String lastName)
        {
            this.lastName = lastName;
            return this;
        }

        @Override
        public Person createData(final Person entityInstance)
        {
            return new PersonData(entityInstance,
                    this.firstName,
                    this.lastName);
        }

        @Override
        public PersonUpdater copy(final Person other)
        {
            final Person data = Entity.data(other);
            this.firstName = data.firstName();
            this.lastName = data.lastName();
            return this;
        }
    }
}
