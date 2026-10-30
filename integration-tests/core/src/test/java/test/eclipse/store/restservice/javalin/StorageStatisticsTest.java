package test.eclipse.store.restservice.javalin;

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

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.eclipse.store.storage.restadapter.types.ViewerStorageFileStatistics;
import org.junit.jupiter.api.Test;


class StorageStatisticsTest extends AbstractIntegrationTest {
    final static String URL = "http://localhost:4567/store-data/maintenance/filesStatistics";

    @Test
    void responseStatusOK() throws IOException {
        final HttpResponse response = get(URL);
        assertThat(response.getCode(), equalTo(HttpStatus.SC_OK));
    }

	@Test
	void contentTypeJSON_fix() throws IOException {
		try (final CloseableHttpResponse response = get(URL)) {
			final Header header = response.getFirstHeader("Content-Type");
			assertNotNull(header);
			final String mimeType = header.getValue().split(";")[0].trim();
			assertEquals("application/json", mimeType);
		}
	}

    @Test
    void contentTypeJSON_explicit() throws IOException {
        try (final CloseableHttpResponse response = get(URL + "?format=json")) {
            final Header header = response.getFirstHeader("Content-Type");
            assertNotNull(header);
            final String mimeType = header.getValue().split(";")[0].trim();
            assertEquals("application/json", mimeType);
        }
    }

    @Test
    void contentTypeFail() throws IOException, ParseException
	{
        try (final CloseableHttpResponse response = get(URL + "?format=NotExisting")) {
            assertThat(response.getCode(), equalTo(HttpStatus.SC_NOT_FOUND));
            assertThat(EntityUtils.toString(response.getEntity()), containsString("invalid url parameter format"));
        }
    }

    @Test
    void deserializeAsObject() throws IOException, ParseException
	{
        final ViewerStorageFileStatistics statistics = getAs(URL, ViewerStorageFileStatistics.class);

        assertTrue(0 < statistics.getFileCount());
        assertTrue(0 < statistics.getLiveDataLength());
        assertTrue(0 < statistics.getTotalDataLength());
        assertNotNull(statistics.getCreationTime());

        assertTrue(0 < statistics.getChannelStatistics().size());
        assertEquals(0, statistics.getChannelStatistics().get(0).getChannelIndex());
        assertTrue(0 < statistics.getChannelStatistics().get(0).getFileCount());

        assertTrue(Files.exists(Paths.get(statistics.getChannelStatistics().get(0).getFiles().get(0).getFile())));
        assertTrue(0 < statistics.getChannelStatistics().get(0).getFiles().get(0).getFileCount());
        assertTrue(0 < statistics.getChannelStatistics().get(0).getFiles().get(0).getFileNumber());
        assertTrue(0 < statistics.getChannelStatistics().get(0).getFiles().get(0).getLiveDataLength());
        assertTrue(0 < statistics.getChannelStatistics().get(0).getFiles().get(0).getTotalDataLength());
    }

}
