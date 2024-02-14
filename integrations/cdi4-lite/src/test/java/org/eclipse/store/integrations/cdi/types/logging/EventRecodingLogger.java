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

import java.util.Queue;

import org.slf4j.Marker;
import org.slf4j.event.Level;
import org.slf4j.event.SubstituteLoggingEvent;
import org.slf4j.helpers.LegacyAbstractLogger;
import org.slf4j.helpers.SubstituteLogger;

/**
 * 10   *
 * 11   * This class is used to record events during the initialization phase of the
 * 12   * underlying logging framework. It is called by {@link SubstituteLogger}.
 * 13   *
 * 14   *
 * 15   * @author Ceki G&uuml;lc&uuml;
 * 16   * @author Wessel van Norel
 * 17   *
 * 18
 */
public class EventRecodingLogger extends LegacyAbstractLogger
{

    private static final long serialVersionUID = -176083308134819629L;

    String name;
    SubstituteLogger logger;
    Queue<SubstituteLoggingEvent> eventQueue;

    // as an event recording logger we have no choice but to record all events
    final static boolean RECORD_ALL_EVENTS = true;

    public EventRecodingLogger(SubstituteLogger logger, Queue<SubstituteLoggingEvent> eventQueue)
    {
        this.logger = logger;
        this.name = logger.getName();
        this.eventQueue = eventQueue;
    }

    public String getName()
    {
        return name;
    }

    public boolean isTraceEnabled()
    {
        return RECORD_ALL_EVENTS;
    }

    public boolean isDebugEnabled()
    {
        return RECORD_ALL_EVENTS;
    }

    public boolean isInfoEnabled()
    {
        return RECORD_ALL_EVENTS;
    }

    public boolean isWarnEnabled()
    {
        return RECORD_ALL_EVENTS;
    }

    public boolean isErrorEnabled()
    {
        return RECORD_ALL_EVENTS;
    }

    // WARNING: this method assumes that any throwable is properly extracted
    protected void handleNormalizedLoggingCall(Level level, Marker marker, String msg, Object[] args, Throwable throwable)
    {
        SubstituteLoggingEvent loggingEvent = new SubstituteLoggingEvent();
        loggingEvent.setTimeStamp(System.currentTimeMillis());
        loggingEvent.setLevel(level);
        loggingEvent.setLogger(logger);
        loggingEvent.setLoggerName(name);
        if (marker != null)
        {
            loggingEvent.addMarker(marker);
        }
        loggingEvent.setMessage(msg);
        loggingEvent.setThreadName(Thread.currentThread().getName());

        loggingEvent.setArgumentArray(args);
        loggingEvent.setThrowable(throwable);

        eventQueue.add(loggingEvent);

    }

    @Override
    protected String getFullyQualifiedCallerName()
    {
        return null;
    }
}
