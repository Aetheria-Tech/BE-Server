package com.serverbe.application.port.out.crypto;

/**
 * @responsibility 시스템 내 민감 데이터의 기밀성을 보장하기 위한 암호화 및 복호화 기능을 정의하는 아웃바운드 포트입니다.
 * 암호화 알고리즘의 구체적인 구현(AES, RSA 등)을 추상화하여 도메인 로직이 보안 기술의 변화에 독립적으로 유지되도록 합니다.
 */
public interface EncryptPort {

    /**
     * @responsibility 전달받은 평문 데이터를 보안 정책에 따라 암호화된 문자열로 변환합니다.
     * @param plainText 암호화할 원본 평문 문자열
     * @return 암호화 처리가 완료되어 안전하게 저장 가능한 형태의 {@link String}
     */
    String encrypt(String plainText);

    /**
     * @responsibility 암호화된 문자열을 해석하여 원래의 평문 데이터로 복원합니다.
     * @param cipherText 복호화 대상이 되는 암호화된 문자열
     * @return 복호화 프로세스를 통해 복원된 원본 {@link String}
     */
    String decrypt(String cipherText);

    /**
     * @responsibility 주어진 암호문이 현재 시스템에서 사용하는 최신 암호화 정책(키 버전, 알고리즘 등)으로 생성되었는지 확인합니다.
     * @param cipherText 버전 확인 대상이 되는 암호화 문자열
     * @return 최신 정책이 적용된 상태면 true, 정책 변경으로 인해 재암호화가 필요하면 false
     */
    boolean isLatestVersion(String cipherText);
}