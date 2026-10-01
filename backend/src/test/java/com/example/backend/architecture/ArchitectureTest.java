package com.example.backend.architecture;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.core.domain.JavaClasses;
import java.util.List;
import java.util.stream.StreamSupport;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;

// 기능 간 공개 service 경계와 역방향 의존을 검사한다.
@Tag("architecture")
class ArchitectureTest {
    private static final String ROOT = "com.example.backend.";
    private static final JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.example.backend", "com.example.platform");
    private static final List<String> features = StreamSupport.stream(classes.spliterator(), false)
            .map(type -> type.getPackageName()).filter(name -> name.startsWith(ROOT))
            .map(name -> name.substring(ROOT.length()).split("\\.")[0])
            .filter(name -> !name.equals("common")).distinct().sorted().toList();

    @Test
    void servicesAndRepositoriesDoNotDependOnWeb() {
        noClasses().that().resideInAnyPackage("..service..", "..repository..")
                .should().dependOnClassesThat().resideInAPackage("..web..")
                .check(classes);
    }

    @Test
    void repositoriesDoNotDependOnServices() {
        noClasses().that().resideInAPackage("..repository..")
                .should().dependOnClassesThat().resideInAPackage("..service..")
                .check(classes);
    }

    @Test
    void publishedServicesHideImplementationsAndPersistence() {
        Assertions.assertThat(features).isNotEmpty();
        for (String feature : features) {
            // 정확히 service 패키지만 공개한다. service.impl은 이 선택자에 포함되지 않는다.
            noClasses().that().resideInAPackage(ROOT + feature + ".service")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..service.impl..", "..repository..", "org.springframework.data..", "jakarta.persistence..")
                    .check(classes);
            // 공개 record가 내부 model·Entity를 필드/메서드 타입으로 노출하지 않는다.
            noClasses().that().resideInAPackage(ROOT + feature + ".service").and().areRecords()
                    .should().dependOnClassesThat().resideOutsideOfPackages("java..", ROOT + feature + ".service")
                    .allowEmptyShould(true).check(classes);
        }
    }

    @Test
    void otherFeaturesOnlyUseTheExactServicePackage() {
        for (String target : features) {
            for (String source : features) {
                if (source.equals(target)) continue;
                noClasses().that().resideInAPackage(ROOT + source + "..")
                        .should().dependOnClassesThat(resideInAPackage(ROOT + target + "..")
                                .and(not(resideInAPackage(ROOT + target + ".service"))))
                        .check(classes);
            }
        }
    }

    @Test
    void commonAndPlatformDoNotDependOnFeatures() {
        for (String feature : features) {
            noClasses().that().resideInAPackage(ROOT + "common..")
                    .should().dependOnClassesThat().resideInAPackage(ROOT + feature + "..")
                    .check(classes);
        }
        noClasses().that().resideInAPackage("com.example.platform..")
                .should().dependOnClassesThat().resideInAnyPackage("com.example.backend..", "com.example.gateway..")
                .check(classes);
    }

    @Test
    void featuresHaveNoPackageCycles() {
        // 공개 service만 사용해도 A -> B -> A는 순환이다.
        slices().matching("com.example.backend.(*)..").should().beFreeOfCycles().check(classes);
    }

    @Test
    void everyFeatureHttpHandlerDeclaresMethodPermission() {
        int handlers = 0;
        // feature 추가 시 보안 어노테이션을 빠뜨리면 테스트가 실패한다. URI 목록을 별도로 복제하지 않는다.
        for (var type : classes) {
            if (features.stream().noneMatch(feature -> type.getPackageName().equals(ROOT + feature + ".web")
                    || type.getPackageName().startsWith(ROOT + feature + ".web."))) continue;
            var javaType = type.reflect();
            if (!javaType.isAnnotationPresent(RestController.class)) continue;
            for (var method : javaType.getDeclaredMethods()) {
                if (!AnnotatedElementUtils.hasAnnotation(method, RequestMapping.class)) continue;
                handlers++;
                Assertions.assertThat(AnnotatedElementUtils.hasAnnotation(method, PreAuthorize.class)
                                || AnnotatedElementUtils.hasAnnotation(javaType, PreAuthorize.class))
                        .as("Explicit permission on %s", method).isTrue();
            }
        }
        Assertions.assertThat(handlers).as("Feature HTTP handlers included in the permission check").isPositive();
    }
}
