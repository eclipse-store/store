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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashSet;
import java.util.Set;

import test.eclipse.store.library.types.BinaryHandlerTestData;


public class PersonEntityTest extends AbstractHandlerTest<PersonEntityTest.DataRoot> {

    static Person create = PersonCreator.New().firstName("John").lastName("Doe").create();

    public PersonEntityTest() {
        super(DataRoot.class);
    }

    @Override
    public void proveResult(DataRoot original, DataRoot copy) {
        //System.out.println(copy);
        Person copyPerson = copy.getPersons().stream().findFirst().get();
        assertEquals(create.firstName(), copyPerson.firstName());
    }

    public static class DataRoot implements BinaryHandlerTestData {

        Set<Person> persons = new HashSet<>();

        @Override
        public BinaryHandlerTestData fillSampleData() {
            persons.add(create);
            return this;
        }

        @Override
        public void proveResults(Object o) {

        }

        public Set<Person> getPersons() {
            return persons;
        }
    }


}
