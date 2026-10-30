package one.microstream.afs.sql;

/*-
 * #%L
 * EclipseStore Integration Tests - AFS - sql-oracle
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

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import org.testcontainers.containers.OracleContainer;
import org.testcontainers.utility.DockerImageName;

import oracle.jdbc.pool.OracleDataSource;

public class Container {

    public static OracleContainer oracle;

    static {
        // CI runs the pinned XE image; -Dafs.oracle.image=gvenzl/oracle-free:23-slim-faststart for arm64 hosts
        // (no XE image exists for arm64).
        final String image = System.getProperty("afs.oracle.image", "gvenzl/oracle-xe:21-slim-faststart");
        oracle = new OracleContainer(DockerImageName.parse(image).asCompatibleSubstituteFor("gvenzl/oracle-xe"))
                .withPassword("Oracle18")
                // The database registers its services with the listener through the container's host name
                // (local_listener is unset). On some Docker hosts that name does not resolve inside the
                // container and the PDB service (xepdb1) is never registered: every connection then fails
                // with ORA-12514 although the listener is up. "localhost" always resolves, see
                // https://github.com/gvenzl/oci-oracle-xe/issues/225.
                .withConnectTimeoutSeconds(360).withStartupTimeoutSeconds(360);
        if (image.contains("oracle-free")) {
            // withDatabaseName becomes ORACLE_DATABASE, which makes the image CREATE that pluggable database;
            // naming the built-in freepdb1 fails with ORA-65012, so an extra PDB is created for the tests.
            oracle.withDatabaseName("afstest");
        } else {
            // XE only, see the comment above; Oracle Free does not start with this host name.
            oracle.withCreateContainerCmdModifier(cmd -> cmd.withHostName("localhost"));
        }
        oracle.start();
        waitUntilReachableViaJdbc();
    }

    /**
     * The container's wait strategy only watches the "DATABASE IS READY TO USE!" log line. The PDB service
     * (xepdb1) registers with the listener asynchronously after that, so the first connection can fail with
     * ORA-12514 "Service xepdb1 is not registered with the listener". {@code createConnection} retries on
     * {@link SQLException} until {@code connectTimeoutSeconds}, which bridges that gap.
     */
    private static void waitUntilReachableViaJdbc() {
        try (Connection connection = oracle.createConnection("")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("SELECT 1 FROM DUAL");
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Oracle container did not become reachable via JDBC", e);
        }
    }

    public static OracleDataSource prepareDataSource() {
        OracleDataSource dataSource;
        try {
            dataSource = new OracleDataSource();
            dataSource.setURL(oracle.getJdbcUrl());
            dataSource.setUser("system");
            dataSource.setPassword(oracle.getPassword());
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return dataSource;
    }
}
