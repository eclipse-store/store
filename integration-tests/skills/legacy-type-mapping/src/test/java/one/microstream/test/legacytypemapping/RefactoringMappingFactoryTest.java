package one.microstream.test.legacytypemapping;

/*-
 * #%L
 * EclipseStore Integration Tests - Skills - legacy-type-mapping
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

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.eclipse.serializer.persistence.types.Persistence;
import org.eclipse.serializer.persistence.types.PersistenceRefactoringMappingProvider;
import org.eclipse.serializer.util.X;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifies each {@link Persistence#RefactoringMapping} overload that the
 * legacy-type-mapping skill claims exists actually returns a non-null
 * {@link PersistenceRefactoringMappingProvider}.
 */
class RefactoringMappingFactoryTest
{
    @Test
    void path_overload_picks_separator_from_file_extension(@TempDir final Path tmp) throws IOException
    {
        // SKILL.md "Explicit mapping via CSV": Persistence.RefactoringMapping(Path)
        // picks the separator from the file extension via XCsvDataType:
        //   .tsv / .xcsv  → preferred separator is tab
        //   .csv          → preferred separator is comma
        // There is no auto-detect fallback. Mismatched content fails with
        // ArrayIndexOutOfBoundsException at dictionary-analysis time.
        final Path tsv = tmp.resolve("refactorings.tsv");
        Files.writeString(tsv,
            "old\tcurrent\n"
            + "com.myapp.Customer#customerid\tcom.myapp.Customer#pin\n"
        );

        final PersistenceRefactoringMappingProvider provider =
            Persistence.RefactoringMapping(tsv);

        assertNotNull(provider, ".tsv with tab content must parse via the Path overload");
    }

    @Test
    void path_with_semicolon_content_via_readString_inline_overload(@TempDir final Path tmp) throws IOException
    {
        // SKILL.md recommended pattern when the on-disk file uses ';' — read
        // the content manually and pass the separator explicitly. Avoids the
        // file-extension/separator mismatch entirely.
        final Path csv = tmp.resolve("refactorings.csv");
        Files.writeString(csv,
            "old;current\n"
            + "com.myapp.Customer#customerid;com.myapp.Customer#pin\n"
        );

        final PersistenceRefactoringMappingProvider provider =
            Persistence.RefactoringMapping(Files.readString(csv), ';');

        assertNotNull(provider,
            "Files.readString + explicit ';' separator must parse the inline content");
    }

    @Test
    void inline_string_overload_uses_default_tab_separator()
    {
        // The single-arg String overload parses with the default XCSV separator (tab).
        final String csv =
            "old\tcurrent\n"
            + "com.myapp.Customer#customerid\tcom.myapp.Customer#pin\n";

        final PersistenceRefactoringMappingProvider provider =
            Persistence.RefactoringMapping(csv);

        assertNotNull(provider, "Inline-string (default-separator) overload must return a non-null provider");
    }

    @Test
    void inline_string_with_explicit_semicolon_separator()
    {
        final String csv =
            "old;current\n"
            + "com.myapp.Customer#customerid;com.myapp.Customer#pin\n";

        final PersistenceRefactoringMappingProvider provider =
            Persistence.RefactoringMapping(csv, ';');

        assertNotNull(provider, "Inline-string (',') overload must return a non-null provider");
    }

    @Test
    void programmatic_provider_via_PersistenceRefactoringMappingProvider_New()
    {
        // pitfalls-deep-dive #5: PersistenceRefactoringMappingProvider.New(...)
        // accepts an Iterable<KeyValue<String,String>> built with X.List(X.KeyValue(...)).
        final PersistenceRefactoringMappingProvider provider =
            PersistenceRefactoringMappingProvider.New(
                X.List(
                    X.KeyValue(
                        "com.myapp.Customer#customerid",
                        "com.myapp.Customer#pin"
                    )
                )
            );

        assertNotNull(provider, "Programmatic provider must be non-null");
    }
}
