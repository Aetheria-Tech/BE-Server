package com.serverbe.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.SecureRandom;

/**
 * @responsibility 시스템 전반에서 요구되는 <b>보안 관련 인프라 빈(Bean)</b> 설정을 담당하는 클래스입니다.
 * @implSpec 예측 불가능한 난수 생성이 필요한 보안 모듈을 위해 <b>CSPRNG(Cryptographically Secure Pseudo-Random Number Generator)</b> 구현체를 제공합니다.
 */
@Configuration
public class SecurityBeanConfig {

    /**
     * @return 암호학적으로 안전한 난수 생성기 인스턴스
     * @responsibility 암호학적으로 강력한 난수를 생성하는 {@link SecureRandom}을 빈으로 등록합니다.
     * @implNote <b>[SecureRandom의 기술적 상세]</b>
     * 1. <b>보안성</b>: 일반적인 {@code java.util.Random}은 선형 합동 생성기(LCG) 알고리즘을 사용해 예측이 가능하지만,
     * {@link SecureRandom}은 운영체제 레벨의 엔트로피(Entropy)를 소스로 사용하여 <b>예측 불가능성</b>을 보장합니다.<br>
     * 2. <b>난수 소스</b>: Unix 계열의 경우 {@code /dev/random} 혹은 {@code /dev/urandom},
     * Windows의 경우 {@code CryptGenRandom}과 같은 시스템 호출을 통해 높은 품질의 난수를 생성합니다.<br>
     * 3. <b>주요 용도</b>: 세션 ID, <b>OAuth2 리프레시 토큰</b>, 암호화용 <b>IV(Initialization Vector)</b>,
     * 비밀번호 해싱을 위한 <b>솔트(Salt)</b> 생성 등 공격자가 패턴을 파악해서는 안 되는 모든 지점에 사용됩니다.
     */
    @Bean
    public SecureRandom secureRandom() {
        return new SecureRandom();
    }
}