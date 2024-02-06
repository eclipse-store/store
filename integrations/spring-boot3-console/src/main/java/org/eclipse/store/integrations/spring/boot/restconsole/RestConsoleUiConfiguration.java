package org.eclipse.store.integrations.spring.boot.restconsole;

import org.eclipse.store.storage.restclient.app.types.RestClientAppAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@ConditionalOnProperty(value = "org.eclipse.store.console.ui.enabled", havingValue = "true", matchIfMissing = true)
@Configuration
@Import(RestClientAppAutoConfiguration.class)
public class RestConsoleUiConfiguration {
}
