package org.eclipse.store.gigamap.indexer;

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

import org.eclipse.store.gigamap.types.Condition;
import org.eclipse.store.gigamap.types.IndexerInteger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ConditionTest
{

    @Test
    void condition_testMethod_withOr()
    {
        // Create a GigaMap with a custom indexer
        PersonAgeIndexer personAgeIndexer = new PersonAgeIndexer();

        // Add some entities to the GigaMap

        Person person = new Person("John", "Doe", 30);
        Person person1 = new Person("Jane", "Doe", 25);

        Condition<Person> firstCondition = personAgeIndexer.is(30);
        Condition<Person> secondCondition = personAgeIndexer.is(35);

        assertTrue(firstCondition.test(person));

        Condition<Person> personCondition = firstCondition.or(secondCondition);

        assertTrue(personCondition.test(person));
        assertFalse(personCondition.test(person1));
    }

    @Test
    void condition_testMethod_withAnd()
    {
        // Create a GigaMap with a custom indexer
        PersonAgeIndexer personAgeIndexer = new PersonAgeIndexer();

        // Add some entities to the GigaMap

        Person person = new Person("John", "Doe", 30);
        Person person1 = new Person("Jane", "Doe", 25);

        Condition<Person> firstCondition = personAgeIndexer.is(30);
        Condition<Person> secondCondition = personAgeIndexer.is(35);

        assertTrue(firstCondition.test(person));

        Condition<Person> personCondition = firstCondition.and(secondCondition);

        assertFalse(personCondition.test(person));
        assertFalse(personCondition.test(person1));
    }

    @Test
    void linkCondition_testMethod_withAnd()
    {
        // Create a GigaMap with a custom indexer
        PersonAgeIndexer personAgeIndexer = new PersonAgeIndexer();

        // Add some entities to the GigaMap

        Person person = new Person("John", "Doe", 30);
        Person person1 = new Person("Jane", "Doe", 25);

        Condition<Person> firstCondition = personAgeIndexer.is(30);
        Condition<Person> secondCondition = personAgeIndexer.is(35);

        assertTrue(firstCondition.test(person));
        Condition<Person> personCondition = firstCondition.linkCondition(secondCondition, Condition.CREATOR_AND);

        assertFalse(personCondition.test(person));
        assertFalse(personCondition.test(person1));
    }

    @Test
    void linkCondition_testMethod_withOr()
    {
        // Create a GigaMap with a custom indexer
        PersonAgeIndexer personAgeIndexer = new PersonAgeIndexer();

        // Add some entities to the GigaMap

        Person person = new Person("John", "Doe", 30);
        Person person1 = new Person("Jane", "Doe", 25);

        Condition<Person> firstCondition = personAgeIndexer.is(30);
        Condition<Person> secondCondition = personAgeIndexer.is(35);

        assertTrue(firstCondition.test(person));
        Condition<Person> personCondition = firstCondition.linkCondition(secondCondition, Condition.CREATOR_OR);

        assertTrue(personCondition.test(person));
        assertFalse(personCondition.test(person1));
    }

    @Test
    void linkCondition_testMethod_withNewOne_Exception()
    {
        // Create a GigaMap with a custom indexer
        PersonAgeIndexer personAgeIndexer = new PersonAgeIndexer();

        // Add some entities to the GigaMap

        Person person = new Person("John", "Doe", 30);
        Person person1 = new Person("Jane", "Doe", 25);

        Condition<Person> firstCondition = personAgeIndexer.is(30);
        Condition<Person> secondCondition = personAgeIndexer.is(35);

        assertTrue(firstCondition.test(person));
        assertThrows(RuntimeException.class, () -> firstCondition.linkCondition(secondCondition, Condition.CREATOR_INITIAL));

    }


    private static class PersonAgeIndexer extends IndexerInteger.Abstract<Person>
    {
    	@Override
    	protected Integer getInteger(Person entity)
        {
            return entity.getAge();
        }
    }

    private static class Person
    {
        private String firstName;
        private String lastName;
        private int age;

        public Person(String firstName, String lastName, int age)
        {
            this.firstName = firstName;
            this.lastName = lastName;
            this.age = age;
        }

        public String getFirstName()
        {
            return firstName;
        }

        public String getLastName()
        {
            return lastName;
        }

        public int getAge()
        {
            return age;
        }
    }
}
