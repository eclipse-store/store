package org.eclipse.store.integrations.spring.boot.restconsole.configuration;

/*-
 * #%L
 * EclipseStore Integrations SpringBoot Console
 * %%
 * Copyright (C) 2023 - 2024 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

@ConfigurationProperties(prefix = "org.eclipse.store.console")
public class RestConsoleProperties
{

    @NestedConfigurationProperty
    private UIProperties ui = new UIProperties();

    public UIProperties getUi()
    {
        return ui;
    }

    public void setUi(UIProperties ui)
    {
        this.ui = ui;
    }
}
