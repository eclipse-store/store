package org.eclipse.store.integrations.cdi.types.config;

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


import ch.qos.logback.classic.spi.ILoggingEvent;
import org.eclipse.store.integrations.cdi.types.logging.TestAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.slf4j.event.LoggingEvent;

import java.io.File;
import java.io.FilenameFilter;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public abstract class AbstractStorageManagerConverterTest
{

    // We cannot clean messages in BeforeEach (that would clear the messages that are written
    // during initialization of the Weld container and thus also the StorageManagerConverter.
    @BeforeAll
    public static void setup()
    {
        TestAppender.events.clear();
    }

    @AfterEach
    public void cleanup()
    {
        TestAppender.events.clear();
    }

    protected void hasMessage(List<ILoggingEvent> messages, String msg)
    {
        final Optional<ILoggingEvent> loggingEvent = messages.stream()
                .filter(le -> le.getMessage()
                        .equals(msg))
                .findAny();

        Assertions.assertTrue(loggingEvent.isPresent());

    }

    protected void directoryHasChannels(final File storageDirectory, final int channelCount)
    {
        final String[] channelDirectories = storageDirectory.list(this.channelDirectory());
        Assertions.assertNotNull(channelDirectories);
        Assertions.assertEquals(channelCount, channelDirectories.length);
        Arrays.stream(channelDirectories)
                .forEach(
                        c ->
                        {
                            final String[] channelParts = c.split("_");  // It guaranteed starts with 'channel_' so split always has 2 items
                            final String[] dataFiles = new File(storageDirectory, c).list(this.channelDataFile(channelParts[1]));
                            Assertions.assertNotNull(dataFiles);
                            Assertions.assertEquals(1, dataFiles.length);
                        }
                );

    }

    private FilenameFilter channelDirectory()
    {
        return (current, name) -> new File(current, name).isDirectory() && name.startsWith("channel_");
    }

    private FilenameFilter channelDataFile(final String channel)
    {
        return (current, name) -> new File(current, name).isFile() && name.equals("channel_" + channel + "_1.dat");
    }
}
