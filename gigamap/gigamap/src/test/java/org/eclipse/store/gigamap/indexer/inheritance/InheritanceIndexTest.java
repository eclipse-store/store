package org.eclipse.store.gigamap.indexer.inheritance;

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

import org.eclipse.store.gigamap.types.GigaMap;
import org.eclipse.store.gigamap.types.IndexerInteger;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class InheritanceIndexTest
{

    @TempDir
    Path workDir;

    @Test
    void inheritanceIndexTest()
    {
        GigaMap<Employee> employees = GigaMap.New();

        EmployeeAgeIndexer employeeAgeIndexer = new EmployeeAgeIndexer();
        employees.index().bitmap().add(employeeAgeIndexer);

        employees.add(new Employee(25, "John", "MicroStream", "Software Development Center", "Developer"));
        employees.add(new Employee(30, "Jane", "MicroStream", "Software Development Center", "Developer"));
        employees.add(new Employee(35, "Jack", "MicroStream", "Software Development Center", "Developer"));

        assertEquals(1, employees.query(employeeAgeIndexer.is(30)).count());
        assertEquals("Jane", employees.query(employeeAgeIndexer.is(30)).findFirst().get().getName());

        //store test
        try (EmbeddedStorageManager manager = EmbeddedStorage.start(employees, workDir)) {
            assertEquals(1, employees.query(employeeAgeIndexer.is(30)).count());
            assertEquals("Jane", employees.query(employeeAgeIndexer.is(30)).findFirst().get().getName());

            //add another one
            employees.add(new Employee(40, "Jill", "MicroStream", "Software Development Center", "Developer"));
            employees.store();

        }

        //load data test
        try (EmbeddedStorageManager manager = EmbeddedStorage.start(workDir)) {
            GigaMap<Employee> loadedEmployees = (GigaMap<Employee>) manager.root();

            assertEquals(1, loadedEmployees.query(employeeAgeIndexer.is(30)).count());
            assertEquals("Jane", loadedEmployees.query(employeeAgeIndexer.is(30)).findFirst().get().getName());
            assertEquals(1, loadedEmployees.query(employeeAgeIndexer.is(40)).count());
            assertEquals("Jill", loadedEmployees.query(employeeAgeIndexer.is(40)).findFirst().get().getName());
        }

    }


    static class EmployeeAgeIndexer extends IndexerInteger.Abstract<Employee>
    {

        @Override
        protected Integer getInteger(Employee employee)
        {
            return employee.getAge();
        }
    }
}
