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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.junit.jupiter.api.Test;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * The Javalin service exposes the available routes as a JSON array at the storage base path
 * ({@code /store-data/}); each entry is a {@code {url, httpMethod}} object.
 * <p>
 * This is the Javalin counterpart of the SparkJava "live documentation": SparkJava listed the routes
 * at the bare root ({@code /}) with {@code URL}/{@code HttpMethod} fields and served per-route
 * descriptions via OPTIONS. The Javalin service does not implement that OPTIONS documentation, so
 * those checks are intentionally omitted here.
 */
public class IntegrationTest_LiveDocumentationTest extends AbstractIntegrationTest {

    static final String ALL_ROUTES_URL = "http://localhost:4567/store-data/";

    @Test
    void getAllRoutes_Header_Test() throws IOException
    {
        try (final CloseableHttpResponse httpResponse = get(ALL_ROUTES_URL)) {
            assertThat(httpResponse.getCode(), equalTo(HttpStatus.SC_OK));

            final Header header = httpResponse.getFirstHeader("Content-Type");
            assertNotNull(header);
            final String mimeType = header.getValue().split(";")[0].trim();
            assertThat(mimeType, equalTo("application/json"));
        }
    }

    @Test
    void getAllRoutes_ListsExpectedRoutes_Test() throws IOException, ParseException
    {
        final List<String> urls = new ArrayList<>();
        try (final CloseableHttpResponse httpResponse = get(ALL_ROUTES_URL)) {
            final String content = EntityUtils.toString(httpResponse.getEntity());
            final JsonArray routes = new Gson().fromJson(content, JsonArray.class);
            for (final JsonElement element : routes) {
                final JsonObject route = element.getAsJsonObject();
                assertTrue(route.has("url"));
                assertTrue(route.has("httpMethod"));
                assertThat(route.get("httpMethod").getAsString(), equalTo("get"));
                urls.add(route.get("url").getAsString());
            }
        }

        assertTrue(urls.contains("/store-data/root"));
        assertTrue(urls.contains("/store-data/dictionary"));
        assertTrue(urls.contains("/store-data/object/{oid}"));
        assertTrue(urls.contains("/store-data/maintenance/filesStatistics"));
    }
}
