package jasper.config;

import org.springframework.cloud.kubernetes.fabric8.discovery.KubernetesCatalogWatchAutoConfiguration;
import org.springframework.cloud.kubernetes.fabric8.discovery.KubernetesDiscoveryClientAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;

@Profile("kubernetes")
@Import({ KubernetesDiscoveryClientAutoConfiguration.class, KubernetesCatalogWatchAutoConfiguration.class })
@Configuration
public class KubernetesConfig {

}
