-- KEYS[1]: sessionKey
-- KEYS[2]: tokenKey (Target Device Token Key)
-- KEYS[3]: blacklistKey (Old Token Hash Key)
-- ARGV[1]: deviceId
-- ARGV[2]: newRefreshToken Value
-- ARGV[3]: newTokenTTL (millis)
-- ARGV[4]: now (timestamp)
-- ARGV[5]: sessionTTL (millis)
-- ARGV[6]: maxToken
-- ARGV[7]: tokenKeyPrefix
-- ARGV[8]: defaultBlacklistTTL (millis) - TTL 조회 실패 시 대체값

-- 1. 기존 토큰의 남은 수명(PTTL) 조회
local existingTtl = redis.call('PTTL', KEYS[2])
local blacklistTtl = ARGV[8]

-- PTTL이 유효한 경우(0보다 큼) 그 시간을 사용
if existingTtl > 0 then
    blacklistTtl = existingTtl
end

-- 2. 기존 토큰(해시값) 블랙리스트 등록
redis.call('SET', KEYS[3], 'used', 'PX', blacklistTtl)

-- 3. 새 토큰 저장 (덮어쓰기)
redis.call('SET', KEYS[2], ARGV[2], 'PX', ARGV[3])

-- 4. 세션 인덱스 갱신
redis.call('ZADD', KEYS[1], ARGV[4], ARGV[1])
redis.call('PEXPIRE', KEYS[1], ARGV[5])

-- 5. 세션 제한 확인 (save_token과 동일 로직)
local count = redis.call('ZCARD', KEYS[1])
local max = tonumber(ARGV[6])

if count > max then
    local removeCount = count - max
    local oldestDevices = redis.call('ZRANGE', KEYS[1], 0, removeCount - 1)
    for _, oldDeviceId in ipairs(oldestDevices) do
        local oldTokenKey = ARGV[7] .. oldDeviceId
        redis.call('DEL', oldTokenKey)
    end
    redis.call('ZREMRANGEBYRANK', KEYS[1], 0, removeCount - 1)
end

return true