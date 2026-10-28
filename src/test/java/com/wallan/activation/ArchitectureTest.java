package com.wallan.activation;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.wallan.activation", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule controllersShouldOnlyResideInWebPackage =
            classes().that().areAnnotatedWith(RestController.class)
                    .should().resideInAPackage("com.wallan.activation.web..");

    @ArchTest
    static final ArchRule servicesShouldOnlyResideInServicePackage =
            classes().that().areAnnotatedWith(Service.class)
                    .should().resideInAPackage("com.wallan.activation.service..");

    @ArchTest
    static final ArchRule repositoriesShouldOnlyResideInDomainPackage =
            classes().that().areAnnotatedWith(Repository.class)
                    .should().resideInAPackage("com.wallan.activation.domain..");

    @ArchTest
    static final ArchRule servicesShouldNotDependOnWeb =
            noClasses().that().resideInAPackage("com.wallan.activation.service..")
                    .should().dependOnClassesThat().resideInAPackage("com.wallan.activation.web..");

    @ArchTest
    static final ArchRule domainShouldNotDependOnWeb =
            noClasses().that().resideInAPackage("com.wallan.activation.domain..")
                    .should().dependOnClassesThat().resideInAPackage("com.wallan.activation.web..");
}
