package test.eclipse.store.handler;

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

import java.io.File;
import java.io.IOException;
import java.util.Date;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Assertions;

class BinaryHandlerFile extends AbstractHandlerTest<BinaryHandlerFile.FileData> {

    BinaryHandlerFile() {
        super(FileData.class);
    }

    @Override
    public void proveResult(FileData original, FileData copy) {
        Assertions.assertEquals(original.getValue().getName(), copy.getValue().getName());
    }

    static class FileData implements BinaryHandlerTestData {
        File value;

        public void fillSampleData() {
            try {
                String fileName = String.valueOf(new Date().getTime());
                value = File.createTempFile(fileName, ".txt", FileUtils.getTempDirectory());
            } catch (IOException e) {
                throw new RuntimeException("Test Failed + e", e);
            }
        }

        File getValue() {
            return value;
        }
    }
}
