package org.eclipse.store.gigamap.query;

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
import org.eclipse.store.gigamap.types.IndexerString;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MultipleQueriesTest
{

    @Test
    void testDuplicateEntries() {
        GigaMap<Employee> employees = GigaMap.New();
        Employee employee = new Employee(25, "John", "MicroStream", "Software Development Center", "Developer");
        employees.add(employee);
        employees.add(employee);

        assertEquals(2, employees.size(), "GigaMap should allow duplicate entries");
    }

    @Test
    void testMultipleFieldIndexing() {
        GigaMap<Employee> employees = GigaMap.New();
        EmployeeAgeIndexer employeeAgeIndexer = new EmployeeAgeIndexer();
        EmployeeNameIndexer employeeNameIndexer = new EmployeeNameIndexer();

        employees.index().bitmap().add(employeeAgeIndexer);
        employees.index().bitmap().add(employeeNameIndexer);

        employees.add(new Employee(25, "John", "MicroStream", "Software Development Center", "Developer"));
        employees.add(new Employee(30, "Jane", "MicroStream", "Software Development Center", "Developer"));
        employees.add(new Employee(30, "Jane", "MicroStream", "Software Development Center", "Developer"));

        assertEquals(1, employees.query(employeeAgeIndexer.is(25)).count(), "There should be one employee aged 25");
        assertEquals(2, employees.query(employeeNameIndexer.is("Jane")).count(), "There should be one employee named Jane");

        employees.query(employeeNameIndexer.is("Jane")).and(employeeAgeIndexer.is(30)).forEach(employee -> {
            assertEquals("Jane", employee.getName(), "Employee should be named Jane");
            assertEquals(30, employee.getAge(), "Employee should be aged 30");
        });

        long count1 = employees.query(employeeNameIndexer.is("Jane")).and(employeeAgeIndexer.is(30)).count();
        assertEquals(2, count1, "There should be one employee named Jane and aged 30");
    }


    @Test
    void multipleIndexingWithOr()
    {
        GigaMap<Employee> employees = GigaMap.New();
        EmployeeAgeIndexer employeeAgeIndexer = new EmployeeAgeIndexer();
        EmployeeNameIndexer employeeNameIndexer = new EmployeeNameIndexer();

        employees.index().bitmap().add(employeeAgeIndexer);
        employees.index().bitmap().add(employeeNameIndexer);

        employees.add(new Employee(25, "John", "MicroStream", "Software Development Center", "Developer"));
        employees.add(new Employee(30, "Jane", "MicroStream", "Software Development Center", "Developer"));
        employees.add(new Employee(30, "Jane", "MicroStream", "Software Development Center", "Developer"));

        assertEquals(1, employees.query(employeeAgeIndexer.is(25)).count(), "There should be one employee aged 25");
        assertEquals(2, employees.query(employeeNameIndexer.is("Jane")).count(), "There should be one employee named Jane");

        long count1 = employees.query(employeeNameIndexer.is("Jane")).or(employeeAgeIndexer.is(25)).count();
        assertEquals(3, count1, "There should be one employee named Jane or aged 25");
    }

    @Test
    void multipleIndexingTestWithNot()
    {
        GigaMap<Employee> employees = GigaMap.New();
        EmployeeAgeIndexer employeeAgeIndexer = new EmployeeAgeIndexer();
        EmployeeNameIndexer employeeNameIndexer = new EmployeeNameIndexer();

        employees.index().bitmap().add(employeeAgeIndexer);
        employees.index().bitmap().add(employeeNameIndexer);

        employees.add(new Employee(25, "John", "MicroStream", "Software Development Center", "Developer"));
        employees.add(new Employee(30, "Jane", "MicroStream", "Software Development Center", "Developer"));
        employees.add(new Employee(25, "Jane", "MicroStream", "Software Development Center", "Developer"));

        assertEquals(2, employees.query(employeeAgeIndexer.is(25)).count(), "There should be one employee aged 25");
        assertEquals(2, employees.query(employeeNameIndexer.is("Jane")).count(), "There should be one employee named Jane");

        long count1 = employees.query(employeeNameIndexer.is("Jane")).and(employeeAgeIndexer.not(25)).count();
        assertEquals(1, count1, "There should be one employee named Jane and not aged 25");
    }

    @Test
    void multipleAndSameQuery()
    {
        GigaMap<Employee> employees = GigaMap.New();
        EmployeeAgeIndexer employeeAgeIndexer = new EmployeeAgeIndexer();
        EmployeeNameIndexer employeeNameIndexer = new EmployeeNameIndexer();

        employees.index().bitmap().add(employeeAgeIndexer);

        employees.add(new Employee(25, "John", "MicroStream", "Software Development Center", "Developer"));
        employees.add(new Employee(30, "Jane", "MicroStream", "Software Development Center", "Developer"));
        employees.add(new Employee(25, "Jane", "MicroStream", "Software Development Center", "Developer"));

        assertEquals(2, employees.query(employeeAgeIndexer.is(25)).count(), "There should be one employee aged 25");

        long count1 = employees.query(employeeAgeIndexer.is(25)).and(employeeAgeIndexer.is(25)).count();
        assertEquals(2, count1);

        long count2 = employees.query(employeeAgeIndexer.is(25)).or(employeeAgeIndexer.is(25)).count();
        assertEquals(2, count2);
    }

    static class EmployeeAgeIndexer extends IndexerInteger.Abstract<Employee> {
        @Override
        protected Integer getInteger(final Employee employee) {
            return employee.getAge();
        }
    }

    static class EmployeeNameIndexer extends IndexerString.Abstract<Employee> {
        @Override
        protected String getString(Employee employee) {
            return employee.getName();
        }
    }


    static class Employee {
        private final int age;
        private final String name;
        private final String company;
        private final String department;
        private final String role;

        public Employee(int age, String name, String company, String department, String role) {
            this.age = age;
            this.name = name;
            this.company = company;
            this.department = department;
            this.role = role;
        }

        public int getAge() {
            return age;
        }

        public String getName() {
            return name;
        }

        public String getCompany() {
            return company;
        }

        public String getDepartment() {
            return department;
        }

        public String getRole() {
            return role;
        }
    }

}
