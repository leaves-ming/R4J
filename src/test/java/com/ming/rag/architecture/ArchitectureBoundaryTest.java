package com.ming.rag.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.ming.rag.bootstrap.RagApplication;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@AnalyzeClasses(packages = "com.ming.rag", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureBoundaryTest {

    @ArchTest
    static final ArchRule domain_should_not_depend_on_spring =
            noClasses()
                    .that().resideInAPackage("com.ming.rag.domain..")
                    .should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "jakarta.persistence..", "dev.langchain4j..");

    @ArchTest
    static final ArchRule infrastructure_should_not_depend_on_interfaces =
            noClasses()
                    .that().resideInAPackage("com.ming.rag.infrastructure..")
                    .should().dependOnClassesThat().resideInAPackage("com.ming.rag.interfaces..");

    @ArchTest
    static final ArchRule interfaces_should_not_depend_on_infrastructure =
            noClasses()
                    .that().resideInAPackage("com.ming.rag.interfaces..")
                    .should().dependOnClassesThat().resideInAPackage("com.ming.rag.infrastructure..");

    @Test
    void ragApplicationShouldNotExcludeDatasourceOrFlywayAutoConfiguration() {
        var annotation = RagApplication.class.getAnnotation(SpringBootApplication.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.exclude()).isEmpty();
    }
}
