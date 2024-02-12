package org.eclipse.store.integrations.cdi4.types.logging;

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

import org.slf4j.event.EventRecodingLogger;
import org.slf4j.event.Level;
import org.slf4j.event.LoggingEvent;
import org.slf4j.event.SubstituteLoggingEvent;
import org.slf4j.helpers.SubstituteLogger;

import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * SLF4J Test Logger and has static management methods.  Call {@code  reset()} in the {@code  BeforeEach} annotated method so
 * that you are sure that previous messages are cleared (since all messages during all tests are accumulated in this class)
 */
public class TestLogger extends EventRecodingLogger
{

    private static final Queue<SubstituteLoggingEvent> eventQueue = new LinkedBlockingQueue<>();

    public TestLogger(final String name)
    {
        super(new SubstituteLogger(name, eventQueue, true), eventQueue);
    }

    /**
     * Clear all messages
     */
    public static void reset()
    {
        eventQueue.clear();
    }

    /**
     * Returns the logging events (messages) for a specified {@link Level} or all messages. The order is as how the
     * logging events are created.
     *
     * @param level The {@link Level} of the messages you are interested or null if you want them all.
     * @return List with logging events for the specified {@link Level} or all.
     */
    public static List<LoggingEvent> getMessagesOfLevel(final Level level)
    {
        Stream<LoggingEvent> stream = Arrays.stream(eventQueue.toArray(new LoggingEvent[0]));
        if (level != null)
        {
            stream = stream.filter(le -> le.getLevel()
                    .equals(level));
        }
        return stream.collect(Collectors.toList());
    }
}
