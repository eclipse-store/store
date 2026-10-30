package test.microstream.afs.sql.mariadb;

/*-
 * #%L
 * EclipseStore Integration Tests - AFS - sql-mariadb
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

import java.sql.SQLException;

import org.mariadb.jdbc.MariaDbDataSource;
import org.testcontainers.containers.MariaDBContainer;

public class MariaContainer {

    public static MariaDBContainer mariaDB;

    static {
        mariaDB = new MariaDBContainer("mariadb:latest").withDatabaseName("jpa-test").withPassword("test");
        mariaDB.start();
    }

    public static MariaDbDataSource prepareDataSource() {
        MariaDbDataSource dataSource = null;
        try {
            dataSource = new MariaDbDataSource(mariaDB.getJdbcUrl());
        } catch (SQLException e) {
            e.printStackTrace();
        }

        try {
            dataSource.setUser("root");
            dataSource.setPassword("test");
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return dataSource;
    }
}
