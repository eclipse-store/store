package test.eclipse.store.library.types;

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

import org.junit.jupiter.api.Assertions;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PropertiesData implements BinaryHandlerTestData {
    Properties value = new Properties();

    // ===== proposed edge-cases (review & cherry-pick) =====
    // Properties extends Hashtable<Object,Object>. Note on `defaults`:
    //   The Properties handler in persistence/binary intentionally does NOT preserve the protected
    //   `defaults` field (see javadoc on org.eclipse.serializer.persistence.binary.java.util.
    //   BinaryHandlerProperties — JDK gives no public way to query defaults). A separate handler
    //   in persistence/binary-jdk8 DOES preserve it via sun internals. Which behavior is active
    //   depends on which module is on the classpath, so a shared cross-module fixture cannot
    //   meaningfully assert defaults round-trip without knowing the active handler. The defaults
    //   chain probe is therefore deliberately omitted here.
    // specialCharsProperties: in-memory keys/values are raw strings; Properties.store/load file
    //   format escapes are NOT in play — the handler should preserve raw String content.
    private Properties emptyProperties;
    private Properties multiEntryProperties;
    private Properties specialCharsProperties;
    private Properties largeProperties;

    @Override
    public PropertiesData fillSampleData() {
        value.put("key", "value");

        // ===== proposed edge-cases =====
        emptyProperties = new Properties();
        multiEntryProperties = createMultiEntryProperties();
        specialCharsProperties = createSpecialCharsProperties();
        largeProperties = createLargeProperties();

        return this;
    }

    public Properties getValue() {
        return value;
    }

    // ===== proposed edge-cases — getters =====

    public Properties getEmptyProperties() {
        return emptyProperties;
    }

    public Properties getMultiEntryProperties() {
        return multiEntryProperties;
    }

    public Properties getSpecialCharsProperties() {
        return specialCharsProperties;
    }

    public Properties getLargeProperties() {
        return largeProperties;
    }

    Properties createMultiEntryProperties() {
        Properties p = new Properties();
        p.setProperty("user.name", "alice");
        p.setProperty("user.home", "/home/alice");
        p.setProperty("file.encoding", "UTF-8");
        p.setProperty("line.separator", "\n");
        p.setProperty("empty.value", "");
        return p;
    }

    Properties createSpecialCharsProperties() {
        Properties p = new Properties();
        p.setProperty("unicode", "Příliš žluťoučký kůň úpěl ďábelské ódy");
        p.setProperty("with.newlines", "line1\nline2\r\nline3");
        p.setProperty("with.equals", "key=value=more");
        p.setProperty("with.hash", "# this looks like a comment");
        p.setProperty("with.tab.and.backslash", "a\tb\\c");
        p.setProperty("emoji", new String(Character.toChars(0x1F600))); // U+1F600 grinning face
        return p;
    }

    Properties createLargeProperties() {
        Properties p = new Properties();
        for (int i = 0; i < 1000; i++) {
            p.setProperty("key." + i, "value." + i);
        }
        return p;
    }

    @Override
    public void proveResults(Object o) {
        Assertions.assertNotNull(o);
        PropertiesData copy = (PropertiesData) o;
        assertAll("Properties tests",
                () -> assertEquals(this.value, copy.value),

                // ===== proposed edge-case verifications =====
                () -> {
                    if (this.getEmptyProperties() != null) {
                        assertTrue(copy.getEmptyProperties().isEmpty(), "empty Properties remains empty");
                    } else {
                        assertNull(copy.getEmptyProperties());
                    }
                },
                () -> {
                    if (this.getMultiEntryProperties() != null) {
                        assertEquals(this.getMultiEntryProperties(), copy.getMultiEntryProperties(), "multi-entry content");
                        assertEquals("UTF-8", copy.getMultiEntryProperties().getProperty("file.encoding"));
                        assertEquals("", copy.getMultiEntryProperties().getProperty("empty.value"), "empty-string value preserved");
                    } else {
                        assertNull(copy.getMultiEntryProperties());
                    }
                },
                () -> {
                    if (this.getSpecialCharsProperties() != null) {
                        Properties sp = copy.getSpecialCharsProperties();
                        assertEquals("Příliš žluťoučký kůň úpěl ďábelské ódy", sp.getProperty("unicode"));
                        assertEquals("line1\nline2\r\nline3", sp.getProperty("with.newlines"), "raw newlines preserved (no Properties.store-style escaping)");
                        assertEquals("key=value=more", sp.getProperty("with.equals"));
                        assertEquals("# this looks like a comment", sp.getProperty("with.hash"));
                        assertEquals("a\tb\\c", sp.getProperty("with.tab.and.backslash"));
                        assertEquals(new String(Character.toChars(0x1F600)), sp.getProperty("emoji"));
                    } else {
                        assertNull(copy.getSpecialCharsProperties());
                    }
                },
                () -> {
                    if (this.getLargeProperties() != null) {
                        assertEquals(1000, copy.getLargeProperties().size(), "large size");
                        assertEquals("value.0", copy.getLargeProperties().getProperty("key.0"));
                        assertEquals("value.999", copy.getLargeProperties().getProperty("key.999"));
                        assertEquals(this.getLargeProperties(), copy.getLargeProperties(), "large content");
                    } else {
                        assertNull(copy.getLargeProperties());
                    }
                }
        );
    }
}
