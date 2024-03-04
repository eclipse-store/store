package org.eclipse.store.integrations.spring.boot.restconsole;

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

import com.vaadin.flow.spring.VaadinConfigurationProperties;
import jakarta.annotation.PostConstruct;
import org.eclipse.store.integrations.spring.boot.restconsole.configuration.RestConsoleProperties;
import org.eclipse.store.storage.restservice.spring.boot.types.StoreDataRestAutoConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(RestConsoleProperties.class)
@AutoConfigureAfter({
        StoreDataRestAutoConfiguration.class
})
@ComponentScan
public class RestConsoleAutoConfiguration
{
    private final Logger logger = LoggerFactory.getLogger(RestConsoleAutoConfiguration.class);
    private final VaadinConfigurationProperties vaadinProperties;

    public RestConsoleAutoConfiguration(@Autowired(required = false) VaadinConfigurationProperties vaadinProperties)
    {
        this.vaadinProperties = vaadinProperties;
    }

    @PostConstruct
    public void initialize()
    {
        final String prefix;
        if (vaadinProperties != null)
        {
            prefix = vaadinProperties.getUrlMapping();
        } else
        {
            prefix = "/";
        }
        logger.info("[ECLIPSE STORE CONSOLE]: Starting console service: '{}'.", prefix);
    }
}
