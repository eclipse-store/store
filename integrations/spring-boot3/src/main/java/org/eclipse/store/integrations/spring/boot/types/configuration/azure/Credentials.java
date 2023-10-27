package org.eclipse.store.integrations.spring.boot.types.configuration.azure;

/*-
 * #%L
 * microstream-integrations-spring-boot3
 * %%
 * Copyright (C) 2019 - 2023 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

public class Credentials
{

    /**
     * The type of the credentials provider. Supported values are:
     * <ul>
     * <li>"basic"</li>
     * Credentials will be loaded from the credentials.username and credentials.password properties.
     *
     * <li>"shared-key"</li>
     * Credentials will be loaded from the credentials.account-name and credentials.account-key properties.
     * </ul>
     */
    private String type;

    /**
     * The username, used when "credentials.type" is "basic".
     */
    private String username;

    /**
     * The password, used when "credentials.type" is "basic".
     */
    private String password;

    /**
     * The account name, used when "credentials.type" is "shared-key".
     */
    private String accountMame;

    /**
     * The account key, used when "credentials.type" is "shared-key".
     */
    private String accountKey;

    public String getType()
    {
        return type;
    }

    public void setType(String type)
    {
        this.type = type;
    }

    public String getUsername()
    {
        return username;
    }

    public void setUsername(String username)
    {
        this.username = username;
    }

    public String getPassword()
    {
        return password;
    }

    public void setPassword(String password)
    {
        this.password = password;
    }

    public String getAccountMame()
    {
        return accountMame;
    }

    public void setAccountMame(String accountMame)
    {
        this.accountMame = accountMame;
    }

    public String getAccountKey()
    {
        return accountKey;
    }

    public void setAccountKey(String accountKey)
    {
        this.accountKey = accountKey;
    }
}
