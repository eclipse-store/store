package org.microstream.spring.boot.example.advanced.storage;

import org.eclipse.store.integrations.spring.boot.types.configuration.EclipseStoreProperties;
import org.eclipse.store.integrations.spring.boot.types.factories.EmbeddedStorageFoundationFactory;
import org.eclipse.store.integrations.spring.boot.types.factories.EmbeddedStorageManagerFactory;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AdvancedStorageConfiguration {

  private final EmbeddedStorageFoundationFactory foundationFactory;
  private final EmbeddedStorageManagerFactory managerFactory;

  public AdvancedStorageConfiguration(EmbeddedStorageFoundationFactory foundationFactory, EmbeddedStorageManagerFactory managerFactory) {
    this.foundationFactory = foundationFactory;
    this.managerFactory = managerFactory;
  }

  @Bean("jokes")
  @ConfigurationProperties("org.eclipse.store.jokes")
  EclipseStoreProperties jokesStoreProperties() {
    return new EclipseStoreProperties();
  }

  @Bean("muppets")
  @ConfigurationProperties("org.eclipse.store.muppets")
  EclipseStoreProperties muppetsStoreProperties() {
    return new EclipseStoreProperties();
  }

  @Bean
  @Qualifier("jokes")
  EmbeddedStorageManager jokesStore(@Qualifier("jokes") final EclipseStoreProperties jokesStoreProperties) {
    return managerFactory.createStorage(
        foundationFactory.createStorageFoundation(jokesStoreProperties),
        jokesStoreProperties.isAutoStart()
    );
  }

  @Bean
  @Qualifier("muppets")
  EmbeddedStorageManager muppetsStore(@Qualifier("muppets") final EclipseStoreProperties muppetsStoreProperties) {
    return managerFactory.createStorage(
        foundationFactory.createStorageFoundation(muppetsStoreProperties),
        muppetsStoreProperties.isAutoStart()
    );
  }

}
