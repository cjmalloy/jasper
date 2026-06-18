package jasper.config;

import org.springframework.cloud.kubernetes.fabric8.discovery.Fabric8CatalogWatchAutoConfiguration;
import org.springframework.cloud.kubernetes.fabric8.discovery.Fabric8DiscoveryClientAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;

@Profile("kubernetes")
@Import({ Fabric8DiscoveryClientAutoConfiguration.class, Fabric8CatalogWatchAutoConfiguration.class })
@Configuration
public class KubernetesConfig {

}
