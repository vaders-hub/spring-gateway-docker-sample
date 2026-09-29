package com.example.backend.architecture;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.core.domain.JavaClasses;
import org.junit.jupiter.api.Test;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

class ArchitectureTest {
    private static final JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.example.backend");
    // JPA 도입 후에도 Service는 저장소 port만 알고, HTTP DTO나 Entity 구현을 참조하지 않아야 한다.
    @Test
    void applicationDoesNotDependOnApiOrInfrastructure() {
        noClasses().that().resideInAPackage("..application..")
                .should().dependOnClassesThat().resideInAnyPackage("..api..", "..infrastructure..")
                .check(classes);
    }
    @Test
    void apiNeverExposesPersistenceImplementation() {
        noClasses().that().resideInAPackage("..api..")
                .should().dependOnClassesThat().resideInAPackage("..infrastructure..")
                .check(classes);
    }
    @Test
    void commonDoesNotDependOnFeatures() {
        noClasses().that().resideInAPackage("..common..")
                .should().dependOnClassesThat().resideInAnyPackage("..hello..", "..member..", "..product..", "..order..")
                .check(classes);
    }
    @Test
    void featuresHaveNoPackageCycles() {
        slices().matching("com.example.backend.(*)..").should().beFreeOfCycles().check(classes);
    }
    @Test
    void domainAndInfrastructureKeepTheirBoundaries() {

        noClasses().that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideOutsideOfPackages("java..", "com.example.backend..domain..")
                .check(classes);

        noClasses().that().resideInAPackage("..infrastructure..")
                .should().dependOnClassesThat().resideInAPackage("..api..")
                .allowEmptyShould(true).check(classes);
    }
}
