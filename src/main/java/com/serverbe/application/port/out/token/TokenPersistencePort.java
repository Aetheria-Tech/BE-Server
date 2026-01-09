package com.serverbe.application.port.out.token;

import java.time.Duration;

/**
 * @responsibility 리프레시 토큰의 영속성 관리 및 액세스 토큰의 블랙리스트 상태를 관리하는 아웃바운드 포트 인터페이스입니다.
 * 토큰 기반 인증 시스템에서 세션의 유지, 강제 종료, 보안 검증을 위한 데이터 접근 로직을 정의합니다.
 */
public interface TokenPersistencePort {

    /**
     * @responsibility 특정 유저에게 발급된 리프레시 토큰을 유효 기간과 함께 저장소에 등록합니다.
     * @param userId 토큰의 소유자인 사용자의 고유 식별자
     * @param refreshToken 저장할 리프레시 토큰 문자열
     * @param expiry 토큰의 유효 기간을 나타내는 {@link Duration} (저장소의 TTL 설정에 활용)
     */
    void saveRefreshToken(Long userId, String refreshToken, Duration expiry);

    /**
     * @responsibility 유저 식별자를 기반으로 현재 저장된 리프레시 토큰을 조회합니다.
     * @deprecated 이 메서드는 단일 토큰 조회 방식의 한계로 인해 사용을 권장하지 않으며, 향후 다중 기기 대응을 위해 대체될 예정입니다.
     * @param userId 조회의 기준이 되는 사용자의 고유 식별자
     * @return 조회된 리프레시 토큰 문자열
     */
    @Deprecated
    String getRefreshToken(Long userId);

    /**
     * @responsibility 특정 사용자와 연관된 모든 리프레시 토큰을 삭제하여 해당 유저의 모든 인증 세션을 무효화(Global Logout)합니다.
     * @param userId 모든 토큰을 삭제할 사용자의 고유 식별자
     */
    void deleteRefreshToken(Long userId);

    /**
     * @responsibility 로그아웃 처리된 액세스 토큰을 블랙리스트에 등록하여, 토큰이 만료되기 전까지 재사용되지 못하도록 차단합니다.
     * @param accessToken 블랙리스트에 등록할 유효한 액세스 토큰
     * @param remainingTime 해당 토큰이 만료될 때까지 남은 시간 {@link Duration}
     */
    void blacklistAccessToken(String accessToken, Duration remainingTime);

    /**
     * @responsibility 전달된 액세스 토큰이 블랙리스트에 등록되어 무효화된 상태인지 확인합니다.
     * @param accessToken 검증 대상이 되는 액세스 토큰
     * @return 블랙리스트에 존재하여 사용할 수 없는 경우 true, 유효한 경우 false
     */
    boolean isBlacklisted(String accessToken);

    /**
     * @responsibility 특정 사용자의 세션 목록에 해당 리프레시 토큰이 실제로 존재하는지 여부를 확인합니다.
     * @param userId 확인하고자 하는 사용자의 고유 식별자
     * @param refreshToken 존재 여부를 확인할 리프레시 토큰 문자열
     * @return 토큰이 존재하고 유효한 경우 true, 그렇지 않으면 false
     */
    boolean existsRefreshToken(Long userId, String refreshToken);

    /**
     * @responsibility 사용자의 여러 세션 중 특정 리프레시 토큰만을 선택하여 삭제합니다. (주로 토큰 회전이나 개별 기기 로그아웃 시 사용)
     * @param userId 토큰 소유자의 고유 식별자
     * @param refreshToken 삭제 대상이 되는 특정 리프레시 토큰
     */
    void removeSpecificRefreshToken(Long userId, String refreshToken);
}