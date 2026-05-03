-- KEYS[1]: sessionKey
-- KEYS[2]: tokenKey
-- ARGV[1]: deviceId

-- 1. 개별 토큰 데이터 삭제
redis.call('DEL', KEYS[2])

-- 2. 세션 목록(ZSet)에서 해당 디바이스 제거
redis.call('ZREM', KEYS[1], ARGV[1])

return true