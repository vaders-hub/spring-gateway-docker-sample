package com.example.gateway.architecture;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.core.domain.JavaClasses;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

// 임시로 끄려면 -PskipArchitecture 옵션으로 test를 실행한다.
@Tag("architecture")
class ArchitectureTest {
    private static final JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.example.gateway");
    @Test
    void servicesDoNotDependOnWebOrRedisImplementations() {
        noClasses().that().resideInAPackage("..auth.service..")
                .should().dependOnClassesThat().resideInAnyPackage("..auth.web..", "..auth.repository.redis..")
                .check(classes);
    }
    @Test
    void commonDoesNotDependOnFeatures() {
        noClasses().that().resideInAPackage("..common..")
                .should().dependOnClassesThat().resideInAnyPackage("..auth..")
                .check(classes);
    }
    @Test
    void featuresHaveNoPackageCycles() {
        slices().matching("com.example.gateway.(*)..").should().beFreeOfCycles().check(classes);
    }
    @Test
    void repositoriesDoNotDependOnWebOrServices() {
        noClasses().that().resideInAPackage("..auth.repository..")
                .should().dependOnClassesThat().resideInAnyPackage("..auth.web..", "..auth.service..")
                .check(classes);
    }
}
