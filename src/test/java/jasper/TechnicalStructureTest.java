package jasper;

import com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.base.DescribedPredicate.alwaysTrue;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.belongToAnyOf;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

@AnalyzeClasses(packagesOf = JasperApplication.class, importOptions = DoNotIncludeTests.class)
class TechnicalStructureTest {

    // prettier-ignore
    @ArchTest
    static final ArchRule respectsTechnicalArchitectureLayers = layeredArchitecture()
        .layer("Config").definedBy("..config..")
        .layer("Client").definedBy("..client..")
        .layer("Web").definedBy("..web..")
        .layer("Component").definedBy("..component..")
        .layer("Service").definedBy("..service..")
        .layer("Security").definedBy("..security..")
        .layer("Persistence").definedBy("..repository..")
        .layer("Domain").definedBy("..domain..")

        .whereLayer("Config").mayNotBeAccessedByAnyLayer()
        .whereLayer("Client").mayOnlyBeAccessedByLayers("Web", "Component", "Service")
        .whereLayer("Web").mayOnlyBeAccessedByLayers("Config")
        .whereLayer("Component").mayOnlyBeAccessedByLayers("Client", "Service")
        .whereLayer("Service").mayOnlyBeAccessedByLayers("Web", "Component", "Config", "Security")
        .whereLayer("Security").mayOnlyBeAccessedByLayers("Config", "Client", "Service", "Web")
        .whereLayer("Persistence").mayOnlyBeAccessedByLayers("Service", "Component", "Security", "Web", "Config")
        .whereLayer("Domain").mayOnlyBeAccessedByLayers("Persistence", "Client", "Service", "Component", "Security", "Web", "Config")

        .ignoreDependency(belongToAnyOf(JasperApplication.class), alwaysTrue())
        .ignoreDependency(alwaysTrue(), belongToAnyOf(
            jasper.config.Constants.class,
            jasper.config.ApplicationProperties.class
        ));
}
