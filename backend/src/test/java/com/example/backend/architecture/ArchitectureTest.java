package com.example.backend.architecture;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.core.domain.JavaClasses;
import java.util.List;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.base.DescribedPredicate.not;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

class ArchitectureTest {
    private static final String ROOT = "com.example.backend.";
    private static final JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.example.backend");
    // 최상위 업무 패키지를 자동 발견하므로 payment/shipping이 추가돼도 검사 목록 누락을 피한다.
    private static final List<String> features = StreamSupport.stream(classes.spliterator(), false)
            .map(type -> type.getPackageName()).filter(name -> name.startsWith(ROOT))
            .map(name -> name.substring(ROOT.length()).split("\\.")[0])
            .filter(name -> !name.equals("common")).distinct().sorted().toList();

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
        for (String feature : features) {
            noClasses().that().resideInAPackage(ROOT + "common..")
                    .should().dependOnClassesThat().resideInAPackage(ROOT + feature + "..")
                    .check(classes);
        }
    }
    @Test
    void featuresHaveNoPackageCycles() {
        // 공개 contract만 사용해도 A -> B -> A는 순환이다. 인터페이스를 만들었다고 순환이 사라지지 않는다.
        slices().matching("com.example.backend.(*)..").should().beFreeOfCycles().check(classes);
    }
    @Test
    void domainAndInfrastructureKeepTheirBoundaries() {
        noClasses().that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideOutsideOfPackages("java..", ROOT + ".domain..")
                .check(classes);
        noClasses().that().resideInAPackage("..infrastructure..")
                .should().dependOnClassesThat().resideInAPackage("..api..")
                .check(classes);
    }
    @Test
    void otherFeaturesCanOnlyAccessPublishedContracts() {
        for (String feature : features) {
            // public 키워드는 Java 접근성일 뿐이다. 다른 feature에는 contract만 공개한다.
            noClasses().that().resideInAPackage(ROOT + ".")
                    .and().resideOutsideOfPackage(ROOT + feature + "..")
                    .should().dependOnClassesThat(resideInAPackage(ROOT + feature + "..")
                            .and(not(resideInAPackage(ROOT + feature + ".contract.."))))
                    .check(classes);
        }
    }
    @Test
    void onlyIntegrationAdaptersReferToOtherFeatures() {
        for (String source : features) {
            for (String target : features) {
                if (source.equals(target)) continue;
                // 호출자 application은 자체 port만 사용한다. 상대 contract와의 연결은 adapter에 모은다.
                noClasses().that().resideInAPackage(ROOT + source + "..")
                        .and().resideOutsideOfPackage(ROOT + source + ".infrastructure.integration..")
                        .should().dependOnClassesThat().resideInAPackage(ROOT + target + "..")
                        .check(classes);
            }
        }
    }
    @Test
    void contractsDoNotLeakDomainOrFrameworkTypes() {
        for (String feature : features) {
            noClasses().that().resideInAPackage(ROOT + feature + ".contract..")
                    .should().dependOnClassesThat().resideOutsideOfPackages("java..", ROOT + feature + ".contract..")
                    .allowEmptyShould(true).check(classes);
        }
    }
    @Test
    void applicationPortsStayIndependentOfServicesAndFrameworks() {
        for (String feature : features) {
            noClasses().that().resideInAPackage(ROOT + feature + ".application.port..")
                    .should().dependOnClassesThat().resideOutsideOfPackages("java..",
                            ROOT + feature + ".domain..", ROOT + feature + ".application.port..")
                    .allowEmptyShould(true).check(classes);
        }
    }
}
