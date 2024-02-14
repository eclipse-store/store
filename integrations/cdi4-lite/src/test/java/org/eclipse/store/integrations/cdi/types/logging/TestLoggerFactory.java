package org.eclipse.store.integrations.cdi.types.logging;

/*-
 * #%L
 * Eclipse Store Integrations CDI 4 - lite
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


import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Provides TestLogger instances for each class. If a logger for a specific class is asked for repeatedly,
 * it consistently gives back the same logger instance.
 *
 */
public class TestLoggerFactory implements ILoggerFactory
{

    private final ConcurrentMap<String, Logger> loggerMap;

    public TestLoggerFactory()
    {
        this.loggerMap = new ConcurrentHashMap<>();
    }

    public Logger getLogger(final String name)
    {
        return loggerMap.computeIfAbsent(name, TestLogger::new);
    }
}
