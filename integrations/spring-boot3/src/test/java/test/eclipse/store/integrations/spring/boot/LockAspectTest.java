package test.eclipse.store.integrations.spring.boot;

import org.eclipse.store.integrations.spring.boot.types.concurrent.LockAspect;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@TestPropertySource("classpath:application-test.properties")
class LockAspectTest
{

    @Autowired
    ApplicationContext context;

    @Test
    void isLockAspectBeanExists()
    {
        LockAspect bean = context.getBean(LockAspect.class);
        assertNotNull(bean);
    }
}
