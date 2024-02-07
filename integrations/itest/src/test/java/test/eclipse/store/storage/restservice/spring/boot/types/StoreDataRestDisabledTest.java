package test.eclipse.store.storage.restservice.spring.boot.types;

/*-
 * #%L
 * EclipseStore Integrations SpringBoot ITest
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

import org.eclipse.store.storage.restadapter.types.StorageRestAdapter;
import org.eclipse.store.storage.restservice.spring.boot.types.rest.StoreDataRestController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import test.eclipse.store.integration.spring.boot.ITestApplication;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * This test makes sure that the rest service is disabled by default.
 */
@SpringBootTest(
    classes = ITestApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "org.eclipse.store.console.ui.enabled=false", // disable
    }
)
@AutoConfigureMockMvc
public class StoreDataRestDisabledTest {

  @MockBean
  private StorageRestAdapter adapterMock;

  @Autowired
  private ApplicationContext context;

  @Test
  public void controller_is_not_available_if_not_enabled_via_property() {
    assertThatThrownBy(() -> context.getBean(StoreDataRestController.class))
        .isInstanceOf(NoSuchBeanDefinitionException.class)
        .hasMessage("No qualifying bean of type '" + StoreDataRestController.class.getName() + "' available");
  }
}
