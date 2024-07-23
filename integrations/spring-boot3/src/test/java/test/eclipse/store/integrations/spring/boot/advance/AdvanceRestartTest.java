package test.eclipse.store.integrations.spring.boot.advance;

/*-
 * #%L
 * EclipseStore Integrations SpringBoot
 * %%
 * Copyright (C) 2023 - 2024 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import java.nio.file.Path;

import org.eclipse.store.integrations.spring.boot.types.configuration.EclipseStoreProperties;
import org.eclipse.store.integrations.spring.boot.types.factories.EmbeddedStorageFoundationFactory;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageFoundation;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource("classpath:application-advance-restart-test.properties")
@ActiveProfiles("advance_restart_storage")
public class AdvanceRestartTest
{
    @Autowired
    private EmbeddedStorageManager manager;

    @Autowired
    private EclipseStoreProperties myConfiguration;

    @Autowired
    EmbeddedStorageFoundationFactory foundationFactory;

    @TempDir
    static Path tempFolder;

    @Test
    void restarts_storage()
    {

        manager.start();
        AdvanceRestartRoot root = (AdvanceRestartRoot) manager.root();
        root.setValue("hello");
        manager.storeRoot();
        manager.shutdown();

        String expectedPath = tempFolder.toAbsolutePath().toString().replace("\\", "/");
        String actualPath = manager.configuration().fileProvider().baseDirectory().toPathString().replace("\\", "/");
        assertEquals(expectedPath.toLowerCase(), actualPath.toLowerCase());

        final EmbeddedStorageFoundation<?> storageFoundation = this.foundationFactory.createStorageFoundation(this.myConfiguration);
        try (EmbeddedStorageManager storage = storageFoundation.start())
        {
            final AdvanceRestartRoot rootFromStorage = (AdvanceRestartRoot) storage.root();
            assertEquals("hello", rootFromStorage.getValue());
        }
    }


    @TestConfiguration
    @Profile("advance_restart_storage")
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
