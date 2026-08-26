package com.serverbe.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
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
     * 도메인은 프레임워크를 몰라야 합니다. Lombok은 컴파일 타임에만 존재하므로 런타임 의존이 아닙니다.
     *
     * @implNote 이 규칙이 켜지기 전까지 {@code ErrorCode}는 {@code HttpStatus}를 반환했고,
     * {@code Address}는 Spring의 {@code StringUtils}를 썼습니다.
     */
    @ArchTest
    static final ArchRule 도메인은_JDK와_Lombok에만_의존한다 = noClasses()
            .that().resideInAPackage("com.serverbe.domain..")
            .should().dependOnClassesThat()
            .resideOutsideOfPackages("com.serverbe.domain..", "java..", "lombok..");

    /**
     * 애플리케이션은 포트만 알면 됩니다. 어댑터나 인프라를 알기 시작하면 교체 가능성이 사라집니다.
     *
     * @implNote 이 규칙이 켜지기 전까지 서비스 다섯 개가 {@code infrastructure.config.properties.*}를
     * 직접 import했습니다. 지금은 {@code application.config}의 순수 레코드를 받습니다.
     */
    @ArchTest
    static final ArchRule 애플리케이션은_어댑터와_인프라를_모른다 = noClasses()
            .that().resideInAPackage("com.serverbe.application..")
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

    /**
     * 흐름을 <b>바깥에서 시작시키는 것</b>은 전부 인바운드 어댑터입니다. 트리거가 HTTP 요청인지,
     * 큐 메시지인지, 시각인지, 스프링 생명주기 이벤트인지는 <b>방향을 바꾸지 않습니다.</b>
     *
     * @implNote 이 규칙이 켜지기 전까지 SQS 리스너는 {@code infrastructure.config.event}에,
     * 좀비 태스크 스케줄러는 {@code infrastructure.scheduler}에 있었습니다. 세 클래스 모두 주석으로는
     * 스스로를 "인바운드 어댑터"라고 부르면서 위치는 인프라였습니다. 주석이 아니라 위치가 사실이어야
     * 합니다. 다만 {@code @PostConstruct}로 프레임워크 내부 이벤트를 구독하는 것(예:
     * {@code CircuitBreakerEventListener})은 진입점이 아니라 관측이므로 여기에 걸리지 않습니다.
     */
    @ArchTest
    static final ArchRule 바깥이_흐름을_시작시키면_인바운드_어댑터다 = classes()
            .that(haveMethodAnnotatedWithAnyOf(
                    "io.awspring.cloud.sqs.annotation.SqsListener",
                    "org.springframework.scheduling.annotation.Scheduled",
                    "org.springframework.context.event.EventListener"))
            .should().resideInAPackage("com.serverbe.adapter.in..");

    /**
     * 메서드에 붙은 애노테이션까지 봐야 하므로 ArchUnit 기본 술어로는 표현되지 않습니다.
     * 클래스 단위 애노테이션과 달리 {@code @SqsListener}·{@code @Scheduled}는 메서드에 붙습니다.
     */
    private static DescribedPredicate<JavaClass> haveMethodAnnotatedWithAnyOf(String... annotationNames) {
        Set<String> names = Set.of(annotationNames);
        return new DescribedPredicate<>("메서드에 " + String.join(" · ", annotationNames) + " 중 하나가 붙어 있다") {
            @Override
            public boolean test(JavaClass javaClass) {
                return javaClass.getMethods().stream()
                        .flatMap(method -> method.getAnnotations().stream())
                        .anyMatch(annotation -> names.contains(annotation.getRawType().getName()));
            }
        };
    }
}
