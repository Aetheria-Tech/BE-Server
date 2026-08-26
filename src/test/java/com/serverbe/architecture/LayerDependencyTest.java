package com.serverbe.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * @responsibility 헥사고날 아키텍처의 계층 간 의존 방향을 테스트로 고정합니다.
 * @implSpec 규칙 이름을 한글로 두어 실패 메시지가 스스로 무엇을 어겼는지 설명하게 합니다.
 * @implNote 이 프로젝트는 오랫동안 {@code package-info.java} 규약과 관례만으로 경계를 지켜 왔고,
 * 그 결과 도메인이 {@code HttpStatus}에 묶이는 등의 위반이 조용히 쌓였습니다. 규약이 아니라
 * 테스트가 경계를 지키게 하는 것이 이 클래스의 존재 이유입니다.
 */
@AnalyzeClasses(
        packages = "com.serverbe",
        importOptions = {ImportOption.DoNotIncludeTests.class, ImportOption.DoNotIncludeJars.class}
)
class LayerDependencyTest {

    /**
     * 도메인은 헥사곤의 가장 안쪽입니다. 바깥의 어떤 것도 알아서는 안 됩니다.
     */
    @ArchTest
    static final ArchRule 도메인은_어댑터와_인프라를_모른다 = noClasses()
            .that().resideInAPackage("com.serverbe.domain..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("com.serverbe.adapter..", "com.serverbe.infrastructure..");

    /**
     * 인바운드 어댑터가 아웃바운드 어댑터를 직접 부르면 애플리케이션 계층을 건너뛰게 됩니다.
     * 두 어댑터는 오직 포트를 통해서만 만나야 합니다.
     */
    @ArchTest
    static final ArchRule 인바운드_어댑터는_아웃바운드_어댑터를_모른다 = noClasses()
            .that().resideInAPackage("com.serverbe.adapter.in..")
            .should().dependOnClassesThat()
            .resideInAPackage("com.serverbe.adapter.out..");
}
