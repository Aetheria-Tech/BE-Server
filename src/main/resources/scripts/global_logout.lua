-- KEYS[1]: 사용자의 세션 인덱스 키 (예: refresh_token:index:100)
-- ARGV[1]: 개별 토큰 키의 접두사 (예: refresh_token:100:)

local sessionKey = KEYS[1]
local tokenKeyPrefix = ARGV[1]

-- 1. ZSet(또는 Set)에 저장된 모든 deviceId 조회
local deviceIds = redis.call('ZRANGE', sessionKey, 0, -1)

-- 2. 조회된 deviceId를 순회하며 개별 토큰 삭제
for _, deviceId in ipairs(deviceIds) do
    local tokenKey = tokenKeyPrefix .. deviceId
    redis.call('DEL', tokenKey)
end

-- 3. 세션 인덱스(목록) 자체 삭제
redis.call('DEL', sessionKey)

return true