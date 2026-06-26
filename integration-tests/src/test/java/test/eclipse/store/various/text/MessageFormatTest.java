package test.eclipse.store.various.text;

/*-
 * #%L
 * EclipseStore Integration Tests
 * %%
 * Copyright (C) 2023 - 2026 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.Date;
import java.util.Locale;

import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class MessageFormatTest
{
    @TempDir
    Path tempDir;

    @Test
    void messageFormatAsFieldInDataClass()
    {
        MessageFormat mf = new MessageFormat("Hello {0}");
        Object[] params = new Object[]{"World"};

        MessageData root = new MessageData(mf);
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root, tempDir)) {
            storageManager.storeRoot();
        }

        MessageData loaded = new MessageData();
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(loaded, tempDir)) {
            assertEquals(mf.format(params), loaded.getFormat().format(params));
        }
    }

    @Test
    void messageFormatWithMultipleParameters()
    {
        MessageFormat mf = new MessageFormat("User {0} has {1} messages and {2} notifications");
        Object[] params = new Object[]{"Alice", 5, 3};

        MessageData root = new MessageData(mf);
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root, tempDir)) {
            storageManager.storeRoot();
        }

        MessageData loaded = new MessageData();
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(loaded, tempDir)) {
            assertEquals("User Alice has 5 messages and 3 notifications", loaded.getFormat().format(params));
        }
    }

    @Test
    @Disabled("https://github.com/eclipse-serializer/serializer/issues/236")
    void messageFormatWithNumberFormat()
    {
        MessageFormat mf = new MessageFormat("The price is {0,number,currency}", Locale.US);
        Object[] params = new Object[]{123.45};

        MessageData root = new MessageData(mf);
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root, tempDir)) {
            storageManager.storeRoot();
        }

        MessageData loaded = new MessageData();
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(loaded, tempDir)) {
            assertEquals(mf.format(params), loaded.getFormat().format(params));
        }
    }

    @Test
    @Disabled("https://github.com/eclipse-serializer/serializer/issues/235")
    void messageFormatWithDateFormat()
    {
        MessageFormat mf = new MessageFormat("Today is {0,date,long}", Locale.US);
        Object[] params = new Object[]{new Date(1610000000000L)};

        MessageData root = new MessageData(mf);
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root, tempDir)) {
            storageManager.storeRoot();
        }

        MessageData loaded = new MessageData();
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(loaded, tempDir)) {
            assertEquals(mf.format(params), loaded.getFormat().format(params));
        }
    }

    @Test
    void messageFormatWithChoiceFormat()
    {
        MessageFormat mf = new MessageFormat("There {0,choice,0#are no files|1#is one file|1<are {0,number,integer} files}");
        Object[] params0 = new Object[]{0};
        Object[] params1 = new Object[]{1};
        Object[] params5 = new Object[]{5};

        MessageData root = new MessageData(mf);
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root, tempDir)) {
            storageManager.storeRoot();
        }

        MessageData loaded = new MessageData();
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(loaded, tempDir)) {
            assertEquals(mf.format(params0), loaded.getFormat().format(params0));
            assertEquals(mf.format(params1), loaded.getFormat().format(params1));
            assertEquals(mf.format(params5), loaded.getFormat().format(params5));
        }
    }

    @Test
    void messageFormatWithEmptyString()
    {
        MessageFormat mf = new MessageFormat("");
        Object[] params = new Object[]{};

        MessageData root = new MessageData(mf);
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root, tempDir)) {
            storageManager.storeRoot();
        }

        MessageData loaded = new MessageData();
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(loaded, tempDir)) {
            assertEquals("", loaded.getFormat().format(params));
        }
    }

    @Test
    void messageFormatWithSpecialCharacters()
    {
        MessageFormat mf = new MessageFormat("Symbol: {0} and emoji: {1}");
        Object[] params = new Object[]{"€£¥", "😀"};

        MessageData root = new MessageData(mf);
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root, tempDir)) {
            storageManager.storeRoot();
        }

        MessageData loaded = new MessageData();
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(loaded, tempDir)) {
            assertEquals("Symbol: €£¥ and emoji: 😀", loaded.getFormat().format(params));
        }
    }

    @Test
    void messageFormatWithQuotedBraces()
    {
        MessageFormat mf = new MessageFormat("Value in '{braces}': {0}");
        Object[] params = new Object[]{"test"};

        MessageData root = new MessageData(mf);
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root, tempDir)) {
            storageManager.storeRoot();
        }

        MessageData loaded = new MessageData();
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(loaded, tempDir)) {
            assertEquals(mf.format(params), loaded.getFormat().format(params));
        }
    }

    @Test
    @Disabled("https://github.com/eclipse-serializer/serializer/issues/236")
    void messageFormatWithDifferentLocales()
    {
        MessageFormat mfUS = new MessageFormat("Number: {0,number}", Locale.US);
        MessageFormat mfFR = new MessageFormat("Nombre: {0,number}", Locale.FRANCE);
        Object[] params = new Object[]{1234.56};

        MessageData root1 = new MessageData(mfUS);
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root1, tempDir.resolve("us"))) {
            storageManager.storeRoot();
        }

        MessageData root2 = new MessageData(mfFR);
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root2, tempDir.resolve("fr"))) {
            storageManager.storeRoot();
        }

        MessageData loaded1 = new MessageData();
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(loaded1, tempDir.resolve("us"))) {
            assertEquals(mfUS.format(params), loaded1.getFormat().format(params));
        }

        MessageData loaded2 = new MessageData();
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(loaded2, tempDir.resolve("fr"))) {
            assertEquals(mfFR.format(params), loaded2.getFormat().format(params));
        }
    }

    @Test
    void messageFormatWithLongString()
    {
        String pattern = "This is a very long message pattern with parameter {0} ".repeat(10);
        MessageFormat mf = new MessageFormat(pattern);
        Object[] params = new Object[]{"TEST"};

        MessageData root = new MessageData(mf);
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root, tempDir)) {
            storageManager.storeRoot();
        }

        MessageData loaded = new MessageData();
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(loaded, tempDir)) {
            assertEquals(mf.format(params), loaded.getFormat().format(params));
        }
    }

    private static class MessageData
    {
        private MessageFormat format;

        public MessageData(MessageFormat format)
        {
            this.format = format;
        }

        public MessageData()
        {
        }

        public MessageFormat getFormat()
        {
            return format;
        }

        public void setFormat(MessageFormat format)
        {
            this.format = format;
        }
    }
}

