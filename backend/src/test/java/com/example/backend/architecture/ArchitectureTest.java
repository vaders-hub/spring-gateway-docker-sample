package com.example.backend.architecture;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.core.domain.JavaClasses;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

// 실제 사고를 막는 핵심 규칙만 둔다. 임시로 끄려면 -PskipArchitecture 옵션으로 test를 실행한다.
@Tag("architecture")
class ArchitectureTest {
    private static final JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.example.backend");

    @Test
    void applicationDoesNotDependOnApiOrInfrastructure() {
        noClasses().that().resideInAPackage("..application..")
                .should().dependOnClassesThat().resideInAnyPackage("..api..", "..infrastructure..")
                .check(classes);
    }

    @Test
    void featuresHaveNoPackageCycles() {
        // 공개 contract만 사용해도 A -> B -> A는 순환이다. 인터페이스를 만들었다고 순환이 사라지지 않는다.
        slices().matching("com.example.backend.(*)..").should().beFreeOfCycles().check(classes);
    }

    @Test
    void everyFeatureHttpHandlerDeclaresMethodPermission() {
        // feature 추가 시 보안 어노테이션을 빠뜨리면 테스트가 실패한다. URI 목록을 별도로 복제하지 않는다.
        for (var type : classes) {
            if (!type.getPackageName().contains(".api")) continue;
            var javaType = type.reflect();
            if (!javaType.isAnnotationPresent(RestController.class)) continue;
            for (var method : javaType.getDeclaredMethods()) {
                if (!AnnotatedElementUtils.hasAnnotation(method, RequestMapping.class)) continue;
                Assertions.assertThat(AnnotatedElementUtils.hasAnnotation(method, PreAuthorize.class)
                                || AnnotatedElementUtils.hasAnnotation(javaType, PreAuthorize.class))
                        .as("Explicit permission on %s", method).isTrue();
            }
        }
    }
}
