package org.eclipse.store.integrations.spring.boot.types.configuration.oraclecloud;

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

public class ConfigFile
{

    /**
     * The path of the config file, if not set the default is used: "~/.oci/config"
     */
    private String path;

    /**
     * The configuration profile to use, if not set "DEFAULT" is used.
     */
    private String profile;

    /**
     * The encoding of the config file.
     */
    private String charset;

    public String getPath()
    {
        return path;
    }

    public void setPath(String path)
    {
        this.path = path;
    }

    public String getProfile()
    {
        return profile;
    }

    public void setProfile(String profile)
    {
        this.profile = profile;
    }

    public String getCharset()
    {
        return charset;
    }

    public void setCharset(String charset)
    {
        this.charset = charset;
    }
}
