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

import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RegexPatternData implements BinaryHandlerTestData {

    Pattern pattern = Pattern.compile("[a-z]");

    @Override
    public RegexPatternData fillSampleData() {
        try {
            pattern = Pattern.compile("[a-z&&[^m-p]]");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return this;
    }

    public Pattern getPattern() {
        return pattern;
    }

    @Override
    public void proveResults(Object o) {
        Assertions.assertNotNull(o);
        RegexPatternData copy = (RegexPatternData) o;
        assertEquals(this.getPattern().toString(), copy.getPattern().toString(), "regex.Pattern");
    }
}
