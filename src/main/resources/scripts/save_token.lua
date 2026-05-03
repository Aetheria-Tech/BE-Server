-- KEYS[1]: sessionKey (ZSet)
-- KEYS[2]: tokenKey (String)
-- ARGV[1]: deviceId
-- ARGV[2]: refreshToken Value
-- ARGV[3]: tokenTTL (millis)
-- ARGV[4]: now (timestamp)
-- ARGV[5]: sessionTTL (millis)
-- ARGV[6]: maxToken (limit)
-- ARGV[7]: tokenKeyPrefix (삭제 시 키 재조합용)

-- 1. 토큰 저장
redis.call('SET', KEYS[2], ARGV[2], 'PX', ARGV[3])

-- 2. 세션 인덱스 업데이트
redis.call('ZADD', KEYS[1], ARGV[4], ARGV[1])
redis.call('PEXPIRE', KEYS[1], ARGV[5])

-- 3. 세션 제한 확인 및 정리
local count = redis.call('ZCARD', KEYS[1])
local max = tonumber(ARGV[6])

if count > max then
    local removeCount = count - max
    -- 가장 오래된 기기 조회
    local oldestDevices = redis.call('ZRANGE', KEYS[1], 0, removeCount - 1)

    for _, oldDeviceId in ipairs(oldestDevices) do
        -- 토큰 키 재조립 후 삭제
        local oldTokenKey = ARGV[7] .. oldDeviceId
        redis.call('DEL', oldTokenKey)
    end
    -- 세션 목록에서 제거
    redis.call('ZREMRANGEBYRANK', KEYS[1], 0, removeCount - 1)
end

return true