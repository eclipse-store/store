package org.eclipse.store.integrations.spring.boot.types.configuration.sql;

/*-
 * #%L
 * spring-boot3
 * %%
 * Copyright (C) 2023 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */


import org.springframework.boot.context.properties.NestedConfigurationProperty;

public class Sql
{

    @NestedConfigurationProperty
    private Mariadb mariadb;

    @NestedConfigurationProperty
    private Oracle oracle;

    @NestedConfigurationProperty
    private Postgres postgres;

    @NestedConfigurationProperty
    private Sqlite sqlite;

    public Mariadb getMariadb()
    {
        return mariadb;
    }

    public void setMariadb(Mariadb mariadb)
    {
        this.mariadb = mariadb;
    }

    public Oracle getOracle()
    {
        return oracle;
    }

    public void setOracle(Oracle oracle)
    {
        this.oracle = oracle;
    }

    public Postgres getPostgres()
    {
        return postgres;
    }

    public void setPostgres(Postgres postgres)
    {
        this.postgres = postgres;
    }

    public Sqlite getSqlite()
    {
        return sqlite;
    }

    public void setSqlite(Sqlite sqlite)
    {
        this.sqlite = sqlite;
    }
}
