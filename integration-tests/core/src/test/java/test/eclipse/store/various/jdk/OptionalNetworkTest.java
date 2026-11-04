package test.eclipse.store.various.jdk;

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

import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.util.Optional;
import java.util.OptionalInt;

import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class OptionalNetworkTest
{
    @TempDir
    Path tempDir;

    @Test
    void shouldStoreAndReloadOptionalPresentAndEmpty()
    {
        Optional<String> present = Optional.of("value");
        Optional<String> empty = Optional.empty();

        OptionalHolder root = new OptionalHolder(present, empty);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root, tempDir)) {
            storageManager.storeRoot();
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(tempDir)) {
            OptionalHolder loaded = (OptionalHolder) storageManager.root();

            assertEquals(present, loaded.getPresent());
            assertEquals(empty, loaded.getEmpty());
        }
    }

    @Test
    void shouldStoreAndReloadOptionalInt()
    {
        OptionalInt oi = OptionalInt.of(7);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(oi, tempDir)) {
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(tempDir)) {
            OptionalInt loaded = (OptionalInt) storageManager.root();

            assertEquals(oi, loaded);
        }
    }

    @Test
    void shouldStoreAndReloadUriUrlInetAddress() throws Exception
    {
        URI uri = new URI("http://example.com/path");
        URL url = new URL("http://example.com/path");
        InetAddress ia = InetAddress.getByName("127.0.0.1");

        NetworkHolder root = new NetworkHolder(uri, url, ia);

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(root, tempDir)) {
            storageManager.storeRoot();
        }

        try (EmbeddedStorageManager storageManager = EmbeddedStorage.start(tempDir)) {
            NetworkHolder loaded = (NetworkHolder) storageManager.root();

            assertEquals(uri, loaded.getUri());
            assertEquals(url, loaded.getUrl());
            assertEquals(ia, loaded.getInetAddress());
        }
    }

    private static class OptionalHolder
    {
        private Optional<String> present;
        private Optional<String> empty;

        public OptionalHolder(Optional<String> present, Optional<String> empty)
        {
            this.present = present;
            this.empty = empty;
        }

        public OptionalHolder()
        {
        }

        public Optional<String> getPresent()
        {
            return present;
        }

        public Optional<String> getEmpty()
        {
            return empty;
        }

        public void setPresent(Optional<String> present)
        {
            this.present = present;
        }

        public void setEmpty(Optional<String> empty)
        {
            this.empty = empty;
        }
    }

    private static class NetworkHolder
    {
        private URI uri;
        private URL url;
        private InetAddress inetAddress;

        public NetworkHolder(URI uri, URL url, InetAddress inetAddress)
        {
            this.uri = uri;
            this.url = url;
            this.inetAddress = inetAddress;
        }

        public NetworkHolder()
        {
        }

        public URI getUri()
        {
            return uri;
        }

        public URL getUrl()
        {
            return url;
        }

        public InetAddress getInetAddress()
        {
            return inetAddress;
        }

        public void setUri(URI uri)
        {
            this.uri = uri;
        }

        public void setUrl(URL url)
        {
            this.url = url;
        }

        public void setInetAddress(InetAddress inetAddress)
        {
            this.inetAddress = inetAddress;
        }
    }
}

