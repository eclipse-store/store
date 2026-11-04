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

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Path;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpOptions;
import org.apache.hc.client5.http.classic.methods.HttpUriRequest;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.eclipse.store.storage.restadapter.types.ViewerObjectDescription;
import org.eclipse.store.storage.restservice.types.StorageRestService;
import org.eclipse.store.storage.restservice.types.StorageRestServiceResolver;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.Gson;

public class AbstractIntegrationTest {
    static final String objectURL_noParams = "http://localhost:4567/store-data/object/";

    @TempDir
    static Path tempDir;
    protected static EmbeddedStorageManager storage;
    protected static StorageRestService service;
    protected static TestGraph testData;

    @BeforeAll
    static void initAll() {
        //testdata
        testData = new TestGraph();

        //create and start storage
        storage = initSourceStorage(tempDir);

        //start rest service
        //service = new StorageRestService(storage);
        service = StorageRestServiceResolver.resolve(storage);
        service.start();
    }

    static EmbeddedStorageManager initSourceStorage(final Path directory) {
        //testdata
        testData = new TestGraph();

        storage = EmbeddedStorage.start(directory);
        storage.setRoot(testData);
        storage.storeRoot();

        return storage;
    }

    @AfterAll
    static void tearDownAll() {
        //stop service
        service.stop();

        //shutdown storage
        storage.shutdown();
    }

    protected static void assertOid(final long expected, final String actual) {
        assertEquals(expected, Long.parseLong(actual));
    }

    protected static void assertTid(final long expected, final String actual) {
        assertEquals(expected, Long.parseLong(actual));
    }

    protected static void assertLength(final long expected, final String actual) {
        assertEquals(expected, Long.parseLong(actual));
    }

    protected static long lookupTypeId(final Object obj) {
        return storage.persistenceManager().typeDictionary().lookupTypeByName(obj.getClass().getName()).typeId();
    }

    protected static long lookupObjectId(final Object obj) {
        return storage.persistenceManager().lookupObjectId(obj);
    }

    protected static ViewerObjectDescription requestObjectByUrl(final String url) throws IOException, ParseException
	{
        final CloseableHttpResponse response = get(url);
        return as(response, ViewerObjectDescription.class);
    }

    static ViewerObjectDescription requestObjectDescription(final long oid) throws IOException, ParseException
	{
        final CloseableHttpResponse response = get(objectURL_noParams + oid);
        return as(response, ViewerObjectDescription.class);
    }

    protected static ViewerObjectDescription testRequestNoParams(final Object obj) throws IOException, ParseException
	{
        final long oid = lookupObjectId(obj);
        final long tid = lookupTypeId(obj);

        final ViewerObjectDescription objectDescription = requestObjectDescription(oid);

        assertOid(oid, objectDescription.getObjectId());
        assertTid(tid, objectDescription.getTypeId());

        return objectDescription;
    }

    static CloseableHttpResponse get(final String URL) throws IOException {
        final HttpUriRequest request = new HttpGet(URL);
		CloseableHttpResponse response = HttpClientBuilder.create().build().execute(request);
        return response;
    }

    static CloseableHttpResponse options(final String URL) throws IOException {
        final HttpUriRequest request = new HttpOptions(URL);
        final CloseableHttpResponse response = HttpClientBuilder.create().build().execute(request);
        return response;
    }

    static void checkStatusCodeAncContentForUrl(final String url, final int code, final String subString) throws IOException, ParseException
	{
        final CloseableHttpResponse httpResponse = get(url);
        assertThat(httpResponse.getCode(), equalTo(code));
        final String s = EntityUtils.toString(httpResponse.getEntity());
        assertThat(s, containsString(subString));
    }

    static void checkStatusCodeForUrl(final String url, final int code) throws IOException {
        final HttpResponse httpResponse = get(url);
        assertThat(httpResponse.getCode(), equalTo(code));
    }

    static <T> T getAs(final String url, final Class<T> clazz) throws IOException, ParseException
	{
        final CloseableHttpResponse response = get(url);
        return as(response, clazz);
    }

    static String getAsString(final String url) throws IOException, ParseException
	{
        final CloseableHttpResponse response = get(url);
        return EntityUtils.toString(response.getEntity());
    }


    static <T> T as(final CloseableHttpResponse response, final Class<T> clazz) throws IOException, ParseException
	{
        final String jsonFromResponse = EntityUtils.toString(response.getEntity());
        return new Gson().fromJson(jsonFromResponse, clazz);
    }

}
