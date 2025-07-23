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

public class Employee extends Person
{
    private String Company;
    private String Department;
    private String Position;

    public Employee(int age, String name, String company, String department, String position)
    {
        super(age, name);
        Company = company;
        Department = department;
        Position = position;
    }

    public void setCompany(String company)
    {
        Company = company;
    }

    public void setDepartment(String department)
    {
        Department = department;
    }

    public void setPosition(String position)
    {
        Position = position;
    }

    public String getCompany()
    {
        return Company;
    }

    public String getDepartment()
    {
        return Department;
    }

    public String getPosition()
    {
        return Position;
    }

    @Override
    public String toString()
    {
        return "Employee{" +
                "Age=" + getAge() +
                ", Name='" + getName() + '\'' +
                ", Company='" + Company + '\'' +
                ", Department='" + Department + '\'' +
                ", Position='" + Position + '\'' +
                '}';
    }
}
