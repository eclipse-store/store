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

import org.eclipse.serializer.persistence.types.Persistence;
import org.eclipse.serializer.persistence.types.PersistenceRefactoringMappingProvider;
import org.junit.jupiter.api.Test;

/**
 * Verifies the {@code pitfalls-deep-dive.md} #5 claim: a semicolon-delimited
 * inline CSV string is parsed by the {@code (String, char)} overload of
 * {@link Persistence#RefactoringMapping}.
 */
class InlineCsvSemicolonOverloadTest
{
    @Test
    void semicolon_inline_csv_produces_non_null_provider()
    {
        final String csv =
            "old;current\n"
            + "com.myapp.Customer#customerid;com.myapp.Customer#pin\n"
            + "com.myapp.Customer#comment;\n"
            + ";com.myapp.Customer#commerceId\n";

        final PersistenceRefactoringMappingProvider provider =
            Persistence.RefactoringMapping(csv, ';');

        assertNotNull(provider);
    }
}
