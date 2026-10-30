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

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.text.Collator;
import java.util.Locale;

import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class CollatorTest
{
    @TempDir
    Path tempDir;

    @Test
    void collatorFrenchPrimaryStrength()
    {
        Collator c = Collator.getInstance(Locale.FRENCH);
        c.setStrength(Collator.PRIMARY);

        CollatorData root = new CollatorData(c);
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root, tempDir)) {
            storageManager.storeRoot();
        }

        CollatorData loaded = new CollatorData();
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(loaded, tempDir)) {
            assertEquals(0, loaded.getCollator().compare("e", "é"));
            assertEquals(Collator.PRIMARY, loaded.getCollator().getStrength());
        }
    }

    @Test
    void collatorSecondaryStrength()
    {
        Collator c = Collator.getInstance(Locale.FRENCH);
        c.setStrength(Collator.SECONDARY);

        CollatorData root = new CollatorData(c);
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root, tempDir)) {
            storageManager.storeRoot();
        }

        CollatorData loaded = new CollatorData();
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(loaded, tempDir)) {
            assertNotEquals(0, loaded.getCollator().compare("e", "é"));
            assertEquals(Collator.SECONDARY, loaded.getCollator().getStrength());
        }
    }

    @Test
    void collatorTertiaryStrength()
    {
        Collator c = Collator.getInstance(Locale.GERMAN);
        c.setStrength(Collator.TERTIARY);

        CollatorData root = new CollatorData(c);
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root, tempDir)) {
            storageManager.storeRoot();
        }

        CollatorData loaded = new CollatorData();
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(loaded, tempDir)) {
            assertNotEquals(0, loaded.getCollator().compare("a", "A"));
            assertEquals(Collator.TERTIARY, loaded.getCollator().getStrength());
        }
    }

    @Test
    void collatorIdenticalStrength()
    {
        Collator c = Collator.getInstance(Locale.US);
        c.setStrength(Collator.IDENTICAL);

        CollatorData root = new CollatorData(c);
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root, tempDir)) {
            storageManager.storeRoot();
        }

        CollatorData loaded = new CollatorData();
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(loaded, tempDir)) {
            assertEquals(0, loaded.getCollator().compare("test", "test"));
            assertNotEquals(0, loaded.getCollator().compare("test", "Test"));
            assertEquals(Collator.IDENTICAL, loaded.getCollator().getStrength());
        }
    }

    @Test
    void collatorWithDecomposition()
    {
        Collator c = Collator.getInstance(Locale.FRENCH);
        c.setDecomposition(Collator.CANONICAL_DECOMPOSITION);

        CollatorData root = new CollatorData(c);
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root, tempDir)) {
            storageManager.storeRoot();
        }

        CollatorData loaded = new CollatorData();
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(loaded, tempDir)) {
            assertEquals(Collator.CANONICAL_DECOMPOSITION, loaded.getCollator().getDecomposition());
        }
    }

    @Test
    void collatorWithFullDecomposition()
    {
        Collator c = Collator.getInstance(Locale.JAPAN);
        c.setDecomposition(Collator.FULL_DECOMPOSITION);

        CollatorData root = new CollatorData(c);
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root, tempDir)) {
            storageManager.storeRoot();
        }

        CollatorData loaded = new CollatorData();
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(loaded, tempDir)) {
            assertEquals(Collator.FULL_DECOMPOSITION, loaded.getCollator().getDecomposition());
        }
    }

    @Test
    void collatorWithNoDecomposition()
    {
        Collator c = Collator.getInstance(Locale.CHINA);
        c.setDecomposition(Collator.NO_DECOMPOSITION);

        CollatorData root = new CollatorData(c);
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root, tempDir)) {
            storageManager.storeRoot();
        }

        CollatorData loaded = new CollatorData();
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(loaded, tempDir)) {
            assertEquals(Collator.NO_DECOMPOSITION, loaded.getCollator().getDecomposition());
        }
    }

    @Test
    void collatorDifferentLocales()
    {
        Collator germanCollator = Collator.getInstance(Locale.GERMAN);
        Collator japaneseCollator = Collator.getInstance(Locale.JAPANESE);
        Collator arabicCollator = Collator.getInstance(new Locale("ar"));

        CollatorData root1 = new CollatorData(germanCollator);
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root1, tempDir.resolve("german"))) {
            storageManager.storeRoot();
        }

        CollatorData root2 = new CollatorData(japaneseCollator);
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root2, tempDir.resolve("japanese"))) {
            storageManager.storeRoot();
        }

        CollatorData root3 = new CollatorData(arabicCollator);
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root3, tempDir.resolve("arabic"))) {
            storageManager.storeRoot();
        }

        CollatorData loaded1 = new CollatorData();
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(loaded1, tempDir.resolve("german"))) {
            assertTrue(loaded1.getCollator().compare("Ä", "A") != 0);
        }

        CollatorData loaded2 = new CollatorData();
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(loaded2, tempDir.resolve("japanese"))) {
            assertEquals(0, loaded2.getCollator().compare("test", "test"));
        }

        CollatorData loaded3 = new CollatorData();
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(loaded3, tempDir.resolve("arabic"))) {
            assertEquals(0, loaded3.getCollator().compare("test", "test"));
        }
    }

    @Test
    void collatorEmptyStrings()
    {
        Collator c = Collator.getInstance(Locale.US);

        CollatorData root = new CollatorData(c);
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root, tempDir)) {
            storageManager.storeRoot();
        }

        CollatorData loaded = new CollatorData();
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(loaded, tempDir)) {
            assertEquals(0, loaded.getCollator().compare("", ""));
        }
    }

    @Test
    void collatorSpecialCharacters()
    {
        Collator c = Collator.getInstance(Locale.US);
        c.setStrength(Collator.PRIMARY);

        CollatorData root = new CollatorData(c);
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root, tempDir)) {
            storageManager.storeRoot();
        }

        CollatorData loaded = new CollatorData();
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(loaded, tempDir)) {
            assertEquals(0, loaded.getCollator().compare("café", "cafe"));
        }
    }

    @Test
    void collatorLongStrings()
    {
        Collator c = Collator.getInstance(Locale.ENGLISH);

        String longText = "Lorem ipsum dolor sit amet, consectetur adipiscing elit. ".repeat(100);

        CollatorData root = new CollatorData(c);
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root, tempDir)) {
            storageManager.storeRoot();
        }

        CollatorData loaded = new CollatorData();
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(loaded, tempDir)) {
            assertEquals(0, loaded.getCollator().compare(longText, longText));
        }
    }

    @Test
    void collatorUnicodeCharacters()
    {
        Collator c = Collator.getInstance(Locale.US);
        c.setStrength(Collator.IDENTICAL);

        CollatorData root = new CollatorData(c);
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root, tempDir)) {
            storageManager.storeRoot();
        }

        CollatorData loaded = new CollatorData();
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(loaded, tempDir)) {
            assertEquals(0, loaded.getCollator().compare("😀", "😀"));
            assertNotEquals(0, loaded.getCollator().compare("😀", "😁"));
        }
    }

    @Test
    void collatorCombinedStrengthAndDecomposition()
    {
        Collator c = Collator.getInstance(Locale.FRENCH);
        c.setStrength(Collator.SECONDARY);
        c.setDecomposition(Collator.CANONICAL_DECOMPOSITION);

        CollatorData root = new CollatorData(c);
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root, tempDir)) {
            storageManager.storeRoot();
        }

        CollatorData loaded = new CollatorData();
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(loaded, tempDir)) {
            assertEquals(Collator.SECONDARY, loaded.getCollator().getStrength());
            assertEquals(Collator.CANONICAL_DECOMPOSITION, loaded.getCollator().getDecomposition());
        }
    }

    @Test
    void collatorClonedInstance()
    {
        Collator c = Collator.getInstance(Locale.GERMAN);
        c.setStrength(Collator.PRIMARY);
        Collator cloned = (Collator) c.clone();

        CollatorData root = new CollatorData(cloned);
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root, tempDir)) {
            storageManager.storeRoot();
        }

        CollatorData loaded = new CollatorData();
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(loaded, tempDir)) {
            assertEquals(Collator.PRIMARY, loaded.getCollator().getStrength());
        }
    }

    @Test
    void collatorWithNullStrings()
    {
        Collator c = Collator.getInstance(Locale.US);

        CollatorData root = new CollatorData(c);
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root, tempDir)) {
            storageManager.storeRoot();
        }

        CollatorData loaded = new CollatorData();
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(loaded, tempDir)) {
            // Test that loaded collator is not null
            assertTrue(loaded.getCollator() != null);
        }
    }

    @Test
    void collatorWithWhitespace()
    {
        Collator c = Collator.getInstance(Locale.US);
        c.setStrength(Collator.TERTIARY);

        CollatorData root = new CollatorData(c);
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root, tempDir)) {
            storageManager.storeRoot();
        }

        CollatorData loaded = new CollatorData();
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(loaded, tempDir)) {
            assertNotEquals(0, loaded.getCollator().compare("test", " test"));
            assertNotEquals(0, loaded.getCollator().compare("test", "test "));
        }
    }

    @Test
    void collatorWithNumericStrings()
    {
        Collator c = Collator.getInstance(Locale.US);

        CollatorData root = new CollatorData(c);
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root, tempDir)) {
            storageManager.storeRoot();
        }

        CollatorData loaded = new CollatorData();
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(loaded, tempDir)) {
            assertTrue(loaded.getCollator().compare("123", "456") < 0);
            assertTrue(loaded.getCollator().compare("999", "100") > 0);
        }
    }

    @Test
    void collatorWithMixedCaseStrings()
    {
        Collator c = Collator.getInstance(Locale.US);
        c.setStrength(Collator.TERTIARY);

        CollatorData root = new CollatorData(c);
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root, tempDir)) {
            storageManager.storeRoot();
        }

        CollatorData loaded = new CollatorData();
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(loaded, tempDir)) {
            assertNotEquals(0, loaded.getCollator().compare("Test", "test"));
            assertNotEquals(0, loaded.getCollator().compare("TEST", "test"));
        }
    }

    @Test
    void collatorChineseLocale()
    {
        Collator c = Collator.getInstance(Locale.CHINA);
        c.setStrength(Collator.PRIMARY);

        CollatorData root = new CollatorData(c);
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root, tempDir)) {
            storageManager.storeRoot();
        }

        CollatorData loaded = new CollatorData();
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(loaded, tempDir)) {
            assertEquals(Collator.PRIMARY, loaded.getCollator().getStrength());
        }
    }

    @Test
    void collatorRussianLocale()
    {
        Collator c = Collator.getInstance(new Locale("ru"));
        c.setStrength(Collator.SECONDARY);

        CollatorData root = new CollatorData(c);
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root, tempDir)) {
            storageManager.storeRoot();
        }

        CollatorData loaded = new CollatorData();
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(loaded, tempDir)) {
            assertEquals(Collator.SECONDARY, loaded.getCollator().getStrength());
        }
    }

    @Test
    void collatorWithAccentedCharactersPrimary()
    {
        Collator c = Collator.getInstance(Locale.FRENCH);
        c.setStrength(Collator.PRIMARY);

        CollatorData root = new CollatorData(c);
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root, tempDir)) {
            storageManager.storeRoot();
        }

        CollatorData loaded = new CollatorData();
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(loaded, tempDir)) {
            assertEquals(0, loaded.getCollator().compare("à", "a"));
            assertEquals(0, loaded.getCollator().compare("ç", "c"));
        }
    }

    @Test
    void collatorWithAccentedCharactersSecondary()
    {
        Collator c = Collator.getInstance(Locale.FRENCH);
        c.setStrength(Collator.SECONDARY);

        CollatorData root = new CollatorData(c);
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root, tempDir)) {
            storageManager.storeRoot();
        }

        CollatorData loaded = new CollatorData();
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(loaded, tempDir)) {
            assertNotEquals(0, loaded.getCollator().compare("à", "a"));
            assertNotEquals(0, loaded.getCollator().compare("ç", "c"));
        }
    }

    @Test
    void collatorDefaultLocale()
    {
        Collator c = Collator.getInstance();

        CollatorData root = new CollatorData(c);
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root, tempDir)) {
            storageManager.storeRoot();
        }

        CollatorData loaded = new CollatorData();
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(loaded, tempDir)) {
            assertTrue(loaded.getCollator() != null);
        }
    }

    @Test
    void collatorWithSingleCharacter()
    {
        Collator c = Collator.getInstance(Locale.US);

        CollatorData root = new CollatorData(c);
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root, tempDir)) {
            storageManager.storeRoot();
        }

        CollatorData loaded = new CollatorData();
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(loaded, tempDir)) {
            assertTrue(loaded.getCollator().compare("a", "b") < 0);
            assertTrue(loaded.getCollator().compare("z", "a") > 0);
        }
    }

    @Test
    void collatorTurkishLocale()
    {
        Collator c = Collator.getInstance(new Locale("tr"));
        c.setStrength(Collator.TERTIARY);

        CollatorData root = new CollatorData(c);
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root, tempDir)) {
            storageManager.storeRoot();
        }

        CollatorData loaded = new CollatorData();
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(loaded, tempDir)) {
            assertEquals(Collator.TERTIARY, loaded.getCollator().getStrength());
        }
    }

    @Test
    void collatorSwedishLocale()
    {
        Collator c = Collator.getInstance(new Locale("sv"));
        c.setStrength(Collator.PRIMARY);

        CollatorData root = new CollatorData(c);
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root, tempDir)) {
            storageManager.storeRoot();
        }

        CollatorData loaded = new CollatorData();
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(loaded, tempDir)) {
            assertEquals(Collator.PRIMARY, loaded.getCollator().getStrength());
        }
    }

    @Test
    void collatorWithPunctuation()
    {
        Collator c = Collator.getInstance(Locale.US);
        c.setStrength(Collator.IDENTICAL);

        CollatorData root = new CollatorData(c);
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root, tempDir)) {
            storageManager.storeRoot();
        }

        CollatorData loaded = new CollatorData();
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(loaded, tempDir)) {
            assertNotEquals(0, loaded.getCollator().compare("test.", "test"));
            assertNotEquals(0, loaded.getCollator().compare("test!", "test?"));
        }
    }

    @Test
    void collatorWithCombiningCharacters()
    {
        Collator c = Collator.getInstance(Locale.US);
        c.setDecomposition(Collator.CANONICAL_DECOMPOSITION);

        CollatorData root = new CollatorData(c);
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root, tempDir)) {
            storageManager.storeRoot();
        }

        CollatorData loaded = new CollatorData();
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(loaded, tempDir)) {
            assertEquals(Collator.CANONICAL_DECOMPOSITION, loaded.getCollator().getDecomposition());
        }
    }

    @Test
    void collatorWithSurrogateCharacters()
    {
        Collator c = Collator.getInstance(Locale.US);
        c.setStrength(Collator.IDENTICAL);

        CollatorData root = new CollatorData(c);
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root, tempDir)) {
            storageManager.storeRoot();
        }

        CollatorData loaded = new CollatorData();
        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(loaded, tempDir)) {
            // Test with mathematical bold script capital letters
            assertTrue(loaded.getCollator().compare("\uD835\uDC00", "\uD835\uDC01") < 0);
        }
    }

    private static class CollatorData
    {
        private Collator collator;

        public CollatorData(Collator collator)
        {
            this.collator = collator;
        }

        public CollatorData()
        {
        }

        public Collator getCollator()
        {
            return collator;
        }

        public void setCollator(Collator collator)
        {
            this.collator = collator;
        }
    }
}

