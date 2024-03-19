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

public class UIProperties
{
    /**
     * Flag controlling if UI console is enabled, defaults to <code>true</code>
     */
    private boolean enabled = true;

    public boolean isEnabled()
    {
        return enabled;
    }

    public void setEnabled(boolean enabled)
    {
        this.enabled = enabled;
    }
}
