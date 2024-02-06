package org.eclipse.store.integrations.spring.boot.restconsole.types;

import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.eclipse.store.storage.restadapter.types.StorageRestAdapter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan
public class EclipseStoreStorageDataRestAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public StorageRestAdapter defaultStorageRestAdapter(EmbeddedStorageManager storage) {
    return StorageRestAdapter.New(storage);
  }

}
