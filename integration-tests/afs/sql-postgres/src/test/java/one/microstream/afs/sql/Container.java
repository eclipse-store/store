package one.microstream.afs.sql;

/*-
 * #%L
 * EclipseStore Integration Tests - AFS - sql-postgres
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

import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

public class Container {

    public static PostgreSQLContainer pgsql;

    static {
        pgsql = new PostgreSQLContainer("postgres:alpine").withDatabaseName("jpa-test").withPassword("test").withUsername("postgres");
        pgsql.start();
    }

    public static PGSimpleDataSource prepareDataSource() {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setUrl(pgsql.getJdbcUrl());
        dataSource.setUser("postgres");
        dataSource.setPassword("test");

        return dataSource;
    }
}
