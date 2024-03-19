package org.eclipse.store.integrations.cdi.types.logging;

/*-
 * #%L
 * EclipseStore Integrations CDI 4
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


import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import org.slf4j.event.LoggingEvent;

public class TestAppender extends AppenderBase<ILoggingEvent>
{
    public static List<ILoggingEvent> events = new ArrayList<>();

    @Override
    protected void append(ILoggingEvent e) {
        events.add(e);
    }

    /**
     * Returns the logging events (messages) for a specified {@link Level} or all messages. The order is as how the
     * logging events are created.
     *
     * @param level The {@link Level} of the messages you are interested or null if you want them all.
     * @return List with logging events for the specified {@link Level} or all.
     */
    public static List<ILoggingEvent> getMessagesOfLevel(final Level level)
    {
        Stream<ILoggingEvent> stream = Arrays.stream(events.toArray(new ILoggingEvent[0]));
        if (level != null)
        {
            stream = stream.filter(le -> le.getLevel()
                    .equals(level));
        }
        return stream.collect(Collectors.toList());
    }
}
