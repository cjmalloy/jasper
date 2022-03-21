package ca.hc.jasper;

import java.lang.annotation.*;

import ca.hc.jasper.config.TestSecurityConfiguration;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Base composite annotation for integration tests.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(classes = { JasperApplication.class, TestSecurityConfiguration.class })
public @interface IntegrationTest {
}
