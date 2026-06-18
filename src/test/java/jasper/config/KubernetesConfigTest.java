package jasper.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionEvaluationReport;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.kubernetes.fabric8.discovery.Fabric8CatalogWatchAutoConfiguration;
import org.springframework.cloud.kubernetes.fabric8.discovery.Fabric8DiscoveryClient;
import org.springframework.cloud.kubernetes.fabric8.discovery.Fabric8DiscoveryClientAutoConfiguration;

class KubernetesConfigTest {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withPropertyValues("spring.profiles.active=kubernetes")
		.withUserConfiguration(KubernetesConfig.class);

	@Test
	void kubernetesProfileImportsDiscoveryAutoConfiguration() {
		contextRunner.run(context -> {
			assertThat(context).hasSingleBean(KubernetesConfig.class);
			assertThat(context).doesNotHaveBean(Fabric8DiscoveryClient.class);

			var report = ConditionEvaluationReport.get(context.getBeanFactory());
			assertThat(report.getConditionAndOutcomesBySource())
				.containsKeys(
					Fabric8DiscoveryClientAutoConfiguration.class.getName(),
					Fabric8CatalogWatchAutoConfiguration.class.getName()
				);
		});
	}
}
