package com.serverbe.application.port.in.ratelimit;

public interface RateLimitUseCase {
    /**
     * 사용자가 속도 제한에 걸리지 않는지 검사하기 위한 메서드
     * @param userId 검사할 사용자의 ID이며 이는 JWT 토큰에서 추출한다.
     * @return 제한에 걸린다면 true 아니면 false를 리턴한다.
     * */
    boolean isAllowedForUser(Long userId);

    /**
     * 인증되지 않은 사용자가 속도 제한에 걸리지 않는지 검사하기 위한 메서드
     * @param ip 검사에 사용할 사용자의 IP.
     * @return 제한에 걸린다면 true 아니면 false를 리턴한다.
     * */
    boolean isAllowedForIp(String ip);
}