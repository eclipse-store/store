package org.eclipse.store.storage.restclient.app.types;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApplicationTest {

  @Autowired
  private ApplicationContext context;

  @Test
  public void should_start_application() {
    assertThat(context).isNotNull();
  }

}
