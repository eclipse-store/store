package test.eclipse.store.integrations.spring.boot;

import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource("classpath:application-storage-create-disable.properties")
@SpringBootTest
@ActiveProfiles("disable_auto_create")
public class DisableStorageAutoCreateTest
{

    @Autowired
    ApplicationContext context;

    @Test
    void name()
    {
       Assertions.assertThrows(NoSuchBeanDefinitionException.class, () ->  context.getBean(EmbeddedStorageManager.class));
    }
}
