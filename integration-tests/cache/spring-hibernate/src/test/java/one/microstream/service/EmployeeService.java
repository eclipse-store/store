package one.microstream.service;

/*-
 * #%L
 * EclipseStore Integration Tests - Cache - Spring Hibernate
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

import one.microstream.data.Employee;

import java.util.List;

public interface EmployeeService {

    List<Employee> findAll();

    long[] findAllAndMeasure();

    Employee selectSome();
}
