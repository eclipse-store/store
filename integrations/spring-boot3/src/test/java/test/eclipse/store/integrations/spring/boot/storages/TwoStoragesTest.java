package test.eclipse.store.integrations.spring.boot.storages;

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

import org.eclipse.store.integrations.spring.boot.types.configuration.EclipseStoreProperties;
import org.eclipse.store.integrations.spring.boot.types.factories.EmbeddedStorageFoundationFactory;
import org.eclipse.store.integrations.spring.boot.types.factories.EmbeddedStorageManagerFactory;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource("classpath:application-two-storages.properties")
@ActiveProfiles("two_storages")
@Import(TwoStoragesTest.TwoBeanConfiguration.class)
public class TwoStoragesTest
{

    @Autowired(required = false)
    @Qualifier("first_storage")
    private EmbeddedStorageManager firstStorage;

    @Autowired(required = false)
    @Qualifier("second_storage")
    private EmbeddedStorageManager secondStorage;

    @Autowired(required = false)
    @Qualifier("defaultEclipseStore")
    private EmbeddedStorageManager defaultEclipseStore;


    @Test
    void sets_up_two_storages()
    {
        assertThat(defaultEclipseStore).isNull(); // conditional will not create a default storage.
        assertThat(firstStorage).isNotNull();
        assertThat(secondStorage).isNotNull();

        assertThat(firstStorage.root().toString()).isEqualTo("FirstRoot{value='First root value'}");
        assertThat(secondStorage.root().toString()).isEqualTo("SecondRoot{intValue=50, c=c}");
    }

    @TestConfiguration
    @Profile("two_storages")
    static class TwoBeanConfiguration
    {

        @Autowired
        private EmbeddedStorageFoundationFactory foundationFactory;
        @Autowired
        private EmbeddedStorageManagerFactory managerFactory;

        @Bean("first_config")
        @ConfigurationProperties("org.eclipse.store.first")
        EclipseStoreProperties firstStoreProperties()
        {
            return new EclipseStoreProperties();
        }

        @Bean("second_config")
        @ConfigurationProperties("org.eclipse.store.second")
        EclipseStoreProperties secondStoreProperties()
        {
            return new EclipseStoreProperties();
        }

        @Bean
        @Qualifier("first_storage")
        EmbeddedStorageManager createFirstStorage(@Qualifier("first_config") final EclipseStoreProperties firstStoreProperties)
        {
            return managerFactory.createStorage(
                    foundationFactory.createStorageFoundation(firstStoreProperties),
                    firstStoreProperties.isAutoStart()
            );
        }

        @Bean
        @Qualifier("second_storage")
        EmbeddedStorageManager createSecondStorage(@Qualifier("second_config") final EclipseStoreProperties secondStoreProperties)
        {
            return managerFactory.createStorage(
                    foundationFactory.createStorageFoundation(secondStoreProperties),
                    secondStoreProperties.isAutoStart()
            );
        }
    }
}
