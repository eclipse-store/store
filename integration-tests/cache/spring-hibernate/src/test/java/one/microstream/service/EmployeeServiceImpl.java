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
import one.microstream.repository.EmployeeRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import jakarta.transaction.Transactional;
import java.util.List;

@Service
public class EmployeeServiceImpl implements EmployeeService {

    EmployeeRepository employeeRepository;

    int max = 7000;


    public EmployeeServiceImpl(EmployeeRepository employeeRepository) {
        this.employeeRepository = employeeRepository;
    }

    @Override
    @Transactional
    @Cacheable("employee_cache")
    public List<Employee> findAll() {

        return (List<Employee>) employeeRepository.findAll(PageRequest.of(0, max)).getContent();
    }

    @Override
    @Transactional
    public long[] findAllAndMeasure() {
        long[] results = new long[3];
        long start = System.currentTimeMillis();
        List<Employee> employees = employeeRepository.findAll(PageRequest.of(0, max)).getContent();
        long stop = System.currentTimeMillis();
        results[0] = stop - start;
        System.out.println(employees.size() + " time : " + (stop - start));

        start = System.currentTimeMillis();
        employees = employeeRepository.findAll(PageRequest.of(0, max)).getContent();
        stop = System.currentTimeMillis();
        results[1] = stop - start;
        System.out.println(employees.size() + " time : " + (stop - start));

        start = System.currentTimeMillis();
        employees = employeeRepository.findAll(PageRequest.of(0, max)).getContent();
        stop = System.currentTimeMillis();
        results[2] = stop - start;
        System.out.println(employees.size() + " time : " + (stop - start));

        return results;
    }

    @Override
    @Transactional
    public Employee selectSome() {
        for (int i = 0; i < 5; i++) {
            long start = System.currentTimeMillis();
            List<Employee> employees = employeeRepository.findAll(PageRequest.of(0, max)).getContent();
            long stop = System.currentTimeMillis();
            System.out.println(employees.size() + " time : " + (stop - start));
        }
        return null;
    }
}
