package test.eclipse.store.integrations.spring.boot;

/*-
 * #%L
 * spring-boot3
 * %%
 * Copyright (C) 2023 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */


import org.eclipse.store.integrations.spring.boot.types.configuration.ConfigurationPair;
import org.eclipse.store.integrations.spring.boot.types.configuration.EclipseStoreProperties;
import org.eclipse.store.integrations.spring.boot.types.factories.EmbeddedStorageFoundationFactory;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageFoundation;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;


@SpringBootTest
@TestPropertySource("classpath:application-run-test.properties")
@Import(RestartStorageBeanTest.RestartStorageBeanConfiguration.class)
@ActiveProfiles("restart_storage")
public class RestartStorageBeanTest
{

    @Autowired
    private EmbeddedStorageFoundationFactory foundationFactory;

    @Autowired
    private EclipseStoreProperties myConfiguration;

    @Autowired
    @Qualifier("restartStorageBean")
    private EmbeddedStorageManager manager;

    @TempDir
    static Path tempFolder;


    @Test
    void restarts_storage()
    {

        final RestartRoot root = new RestartRoot("hello");
        manager.start();
        manager.setRoot(root);
        manager.storeRoot();
        manager.shutdown();

        String expectedPath = tempFolder.toAbsolutePath().toString().replace("\\", "/");
        String actualPath = manager.configuration().fileProvider().baseDirectory().toPathString().replace("\\", "/");
        assertEquals(expectedPath.toLowerCase(), actualPath.toLowerCase());

        final ConfigurationPair pair = new ConfigurationPair("someKey", "someValue");
        final EmbeddedStorageFoundation<?> storageFoundation = this.foundationFactory.createStorageFoundation(this.myConfiguration, pair);
        try (EmbeddedStorageManager storage = storageFoundation.start())
        {
            final RestartRoot rootFromStorage = (RestartRoot) storage.root();
            assertEquals("hello", rootFromStorage.getValue());
        }
    }

    static class RestartRoot
    {
        private String value;

        public RestartRoot(final String value)
        {
            this.value = value;
        }

        public RestartRoot()
        {
        }

        public String getValue()
        {
            return this.value;
        }

        public void setValue(final String value)
        {
            this.value = value;
        }
    }

    @TestConfiguration
    @Profile("restart_storage")
    static class RestartStorageBeanConfiguration
    {

        @Autowired
        private EmbeddedStorageFoundationFactory foundationFactory;

        @Autowired
        @Qualifier("defaultEclipseStoreProperties")
        private EclipseStoreProperties myConfiguration;

        @Bean("restartStorageBean")
        EmbeddedStorageManager restartStorageBean()
        {
            this.myConfiguration.setStorageDirectory(tempFolder.toAbsolutePath().toString());
            final EmbeddedStorageFoundation<?> storageFoundation = this.foundationFactory.createStorageFoundation(this.myConfiguration);
            return storageFoundation.createEmbeddedStorageManager();
        }
    }
}
