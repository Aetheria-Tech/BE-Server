package com.serverbe.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Set;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

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
     * 위 규칙과 <b>같은 경계를 반대 방식으로</b> 잡습니다. 위가 "이것만 안 된다"는 차단 목록이라면
     * 이것은 "이것만 된다"는 허용 목록입니다. <b>차단 목록은 목록에 없는 것이 전부 통과합니다</b> —
     * 우리 패키지 둘만 막고 있었으므로 {@code org.springframework..}은 처음부터 자유로웠습니다.
     * 바로 위의 도메인 규칙({@code 도메인은_JDK와_Lombok에만_의존한다})은 애초에 허용 목록이라
     * 새 프레임워크가 들어와도 막히는데, 애플리케이션만 절반이 열려 있었습니다.
     *
     * @implNote 이 규칙이 켜지기 전까지 {@code UserDataSyncManager}가 스프링의
     * {@code org.springframework.dao.DataIntegrityViolationException}을 잡았습니다. 도메인에
     * <b>이름이 같은</b> 예외가 이미 있어서, 어느 쪽인지는 파일 머리의 import를 봐야만 알 수
     * 있었습니다. 유니크 제약 위반을 도메인 언어로 옮기는 일은 영속성 어댑터의 몫이고,
     * 지금은 {@code UserPersistenceAdapter.save}가 합니다.
     * @implNote 허용 목록은 <b>import가 아니라 ArchUnit이 실제로 보는 의존</b>을 세어서 정했습니다.
     * {@code dependOnClassesThat}은 필드 타입·애노테이션 멤버 타입·람다 대상 타입까지 보므로
     * import 한 줄 없이 들어오는 것들이 있습니다 — {@code org.slf4j.Logger}(Lombok
     * {@code @Slf4j}가 만드는 필드), {@code reactor.util.function.Tuple2}({@code zipWhen}),
     * {@code transaction.annotation.Isolation}·{@code Propagation}({@code @Transactional}의 멤버).
     * 첫째 것 때문에 {@code org.slf4j..}가, 둘째 것 때문에 {@code reactor.core..}가 아니라
     * <b>{@code reactor..}</b> 가 필요합니다.
     * @implNote <b>목록에 무엇을 넣느냐가 곧 선언입니다.</b> 트랜잭션과 Reactor를 허용한다는 것은
     * 그 둘이 이 계층의 어휘라는 뜻이고, 목록 자체가 "애플리케이션이 프레임워크에 얼마나 묶여
     * 있는가"를 한 화면에 드러냅니다. 새 항목을 넣을 때는 <b>왜 허용하는지 주석을 답니다.</b>
     */
    @ArchTest
    static final ArchRule 애플리케이션은_포트와_도메인_안에서만_논다 = noClasses()
            .that().resideInAPackage("com.serverbe.application..")
            .should().dependOnClassesThat()
            .resideOutsideOfPackages(
                    "com.serverbe.application..",
                    "com.serverbe.domain..",
                    "java..",
                    "lombok..",                          // @Slf4j·@RequiredArgsConstructor 와 lombok.Generated
                    "org.slf4j..",                       // @Slf4j 가 만드는 Logger 필드. import 없이 들어온다
                    "org.springframework.stereotype..",  // @Service·@Component — 빈 선언
                    "org.springframework.transaction..", // 트랜잭션 경계를 정하는 곳이 이 계층이다
                    "reactor.."                          // 10번 문서에서 남기기로 결정한 것
            );

    /**
     * <b>catch 절은 위 규칙에 잡히지 않습니다.</b> ArchUnit이 의존으로 세는 것은 필드·파라미터·
     * 반환 타입·호출·애노테이션 같은 것들이고, <b>잡는 예외의 타입은 그중에 없습니다.</b>
     * 바이트코드에서 catch 대상은 예외 테이블에만 적히기 때문입니다. 그래서 잡는 예외는
     * {@code getTryCatchBlocks()}로 따로 봐야 합니다.
     *
     * @implNote 이 규칙이 켜지기 전까지 {@code UserDataSyncManager}가 스프링의
     * {@code org.springframework.dao.DataIntegrityViolationException}을 잡았습니다. 도메인에
     * <b>이름이 같은</b> 예외가 이미 있어서 어느 쪽인지는 파일 머리의 import를 봐야만 알 수
     * 있었고, IDE 자동 import는 둘 중 아무거나 골랐습니다.
     * @implNote <b>위 허용 목록 규칙을 먼저 켜 봤지만 이 위반을 잡지 못했습니다.</b> 잡는 예외가
     * 의존으로 세어지지 않아서입니다. 규칙의 사각지대를 메우려던 규칙에 같은 종류의 사각지대가
     * 있었던 셈이고, 그래서 둘이 필요합니다 — 하나는 <b>쓰는 타입</b>을, 하나는 <b>잡는 타입</b>을
     * 봅니다.
     */
    @ArchTest
    static final ArchRule 애플리케이션은_프레임워크_예외를_잡지_않는다 = noClasses()
            .that().resideInAPackage("com.serverbe.application..")
            .should(catchThrowablesOutsideOf(
                    "com.serverbe.application..",
                    "com.serverbe.domain..",
                    "java.."));

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
     * <b>포트를 구현하면 어댑터입니다.</b> 위 규칙이 인바운드에 대해 말하는 것을 아웃바운드에
     * 대해 말합니다 — 흐름을 바깥에서 시작시키면 인바운드 어댑터이고, 애플리케이션이 바깥에
     * 무언가를 요청하는 통로면 아웃바운드 어댑터입니다. 둘이 짝을 이뤄 어댑터 경계를
     * <b>양방향으로</b> 고정합니다.
     *
     * @implNote 이 규칙이 켜지기 전까지 아웃바운드 포트 18개 중 3개의 구현체가
     * {@code infrastructure}에 있었습니다({@code AesGcmEncryptor}, {@code JwtTokenProvider},
     * {@code JwtTokenResolver}). 동작에는 문제가 없었지만 그 패키지에서 <b>"프레임워크 배선"과
     * "바깥 세계와의 대화"가 섞여</b>, 무엇이 교체 가능한 부품이고 무엇이 스프링을 붙드는
     * 접착제인지 구분되지 않았습니다.
     * @implNote 조건을 <b>이름이 아니라 패키지</b>로 잡은 것이 중요합니다.
     * {@code application.port.out.security}에는 {@code TokenProvider}·{@code TokenResolver}처럼
     * {@code Port}로 끝나지 않는 포트가 있어, 이름 기반 규칙이면 하필 위반 셋 중 둘이 빠집니다.
     */
    @ArchTest
    static final ArchRule 아웃바운드_포트_구현체는_어댑터다 = classes()
            .that().implement(resideInAPackage("com.serverbe.application.port.out.."))
            .should().resideInAPackage("com.serverbe.adapter.out..");

    /**
     * <b>웹 애노테이션이 붙으면 웹 어댑터입니다.</b> 위 두 규칙이 포트를 기준으로 잡지 못하는 것을
     * 잡습니다 — {@code @RestControllerAdvice}는 포트를 구현하지 않지만
     * <b>컨트롤러의 일부</b>입니다. 스프링 MVC가 컨트롤러에서 던져진 예외를 가로채 응답 본문과
     * 상태 코드를 만드는 자리이고, 컨트롤러가 정상 경로에서 하는 일을 예외 경로에서 그대로 합니다.
     *
     * @implNote 이 규칙이 켜지기 전까지 {@code BusinessExceptionHandler}가
     * {@code infrastructure.error}에 있었습니다. 함께 {@code RestApiResponse}(응답 봉투)와
     * {@code ErrorKindHttpStatusMapper}도 인프라에 있어, 인바운드 어댑터를 {@code adapter.in}에
     * 모아 둔 이유가 "진입점은 한곳에서 보인다"였는데 <b>진입점의 응답 규격만 다른 데</b>
     * 있었습니다.
     * @implNote 인바운드 규칙({@code 바깥이_흐름을_시작시키면_인바운드_어댑터다})은 <b>메서드</b>
     * 애노테이션을 봐야 해서 커스텀 술어가 필요했지만, 이쪽은 <b>클래스</b> 애노테이션이므로
     * ArchUnit 기본 술어로 바로 표현됩니다. 세 애노테이션을 모두 나열한 것은
     * {@code @AnalyzeClasses}가 {@code DoNotIncludeJars}라 {@code @RestControllerAdvice}가
     * {@code @ControllerAdvice}의 메타 애노테이션이라는 사실에 기댈 수 없기 때문입니다.
     * @implNote {@code RestApiResponse} 같은 <b>타입</b>은 애노테이션이 없어 이 규칙이 잡지
     * 못합니다. 그건 규칙 대신 문장으로 남겼습니다 — {@code adapter.in.web.response}의
     * {@code package-info.java}를 보세요.
     */
    @ArchTest
    static final ArchRule 웹_애노테이션이_붙은_클래스는_웹_어댑터다 = classes()
            .that().areAnnotatedWith(RestController.class)
            .or().areAnnotatedWith(RestControllerAdvice.class)
            .or().areAnnotatedWith(ControllerAdvice.class)
            .should().resideInAPackage("com.serverbe.adapter.in.web..");

    /**
     * 스레드에 바인딩되는 선언적 트랜잭션은 리액티브 파이프라인 위에서 <b>아무 일도 하지 않습니다.</b>
     * 프록시는 조립된 {@code Mono}가 리턴되는 순간 커밋하고, 실제 DB 작업은 그 뒤
     * {@code boundedElastic} 스레드에서 일어납니다. 트랜잭션은 DB를 한 번도 건드리지 않고 열렸다 닫힙니다.
     *
     * @implNote 이 규칙이 켜지기 전까지 {@code WithdrawService.withdraw}에 무효한 {@code @Transactional}이
     * 붙어 있었습니다. 실제 쓰기를 자기 트랜잭션을 가진 {@code UserDataCleanupManager}에 맡기고 있어서
     * 데이터가 깨지지는 않았지만, 애노테이션이 <b>"여기는 트랜잭션 안이다"라고 잘못 말하고</b> 있었습니다.
     * 리액티브 흐름에서 트랜잭션이 필요하면 {@code AiGenerationService}처럼 {@code TransactionTemplate}을
     * 실행 스레드 안에서 씁니다.
     */
    @ArchTest
    static final ArchRule 트랜잭션_메서드는_리액티브_타입을_반환하지_않는다 = noMethods()
            .that().areAnnotatedWith(Transactional.class)
            .or().areDeclaredInClassesThat().areAnnotatedWith(Transactional.class)
            .should().haveRawReturnType(beReactiveType());

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

    /**
     * 잡는 예외의 타입은 ArchUnit이 의존으로 세지 않으므로 {@code dependOnClassesThat}으로는
     * 표현되지 않습니다. {@code getTryCatchBlocks()}로 직접 훑습니다.
     *
     * @implNote {@code noClasses().should(...)}는 조건을 <b>뒤집어</b> 읽습니다 — 여기서
     * {@code satisfied}로 보고한 것이 곧 위반입니다.
     */
    private static ArchCondition<JavaClass> catchThrowablesOutsideOf(String... allowedPackages) {
        DescribedPredicate<JavaClass> allowed = resideInAnyPackage(allowedPackages);
        return new ArchCondition<>("허용된 패키지 밖의 예외를 catch 한다") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                javaClass.getCodeUnits().forEach(codeUnit ->
                        codeUnit.getTryCatchBlocks().forEach(tryCatchBlock ->
                                tryCatchBlock.getCaughtThrowables().stream()
                                        .filter(caught -> !allowed.test(caught))
                                        .forEach(caught -> events.add(SimpleConditionEvent.satisfied(
                                                javaClass,
                                                String.format("Class <%s> catches <%s> in %s",
                                                        javaClass.getName(),
                                                        caught.getName(),
                                                        tryCatchBlock.getSourceCodeLocation()))))));
            }
        };
    }

    /**
     * 반환 타입을 <b>이름으로</b> 비교합니다. {@code @AnalyzeClasses}가 {@code DoNotIncludeJars}이므로
     * Reactor 타입은 스텁으로 들어오고, {@code isAssignableTo(Publisher.class)} 같은 계층 질의는
     * 풀리지 않을 수 있습니다.
     */
    private static DescribedPredicate<JavaClass> beReactiveType() {
        Set<String> names = Set.of(Mono.class.getName(), Flux.class.getName());
        return new DescribedPredicate<>("Mono 또는 Flux") {
            @Override
            public boolean test(JavaClass javaClass) {
                return names.contains(javaClass.getName());
            }
        };
    }
}
