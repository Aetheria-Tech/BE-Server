-- keys[1]: rate limit key (예: rate:user:100)
-- argv[1]: capacity (버킷 최대 크기 / Burst 허용량)
-- argv[2]: refill rate (초당 충전되는 토큰 수)
-- argv[3]: requested tokens (이번 요청에 필요한 토큰 수, 보통 1)
-- argv[4]: current timestamp (현재 시간 - 밀리초 단위)

local key = keys[1]
local capacity = tonumber(argv[1])
local rate = tonumber(argv[2])
local requested = tonumber(argv[3])
local now = tonumber(argv[4])

-- Redis에서 저장된 마지막 갱신 시간과 현재 토큰 양을 가져옴
local last_tokens = tonumber(redis.call("HGET", key, "tokens"))
local last_refilled = tonumber(redis.call("HGET", key, "last_refilled"))

-- 초기화: 값이 없으면 capacity로 시작
if last_tokens == nil then
  last_tokens = capacity
  last_refilled = now
end

-- 토큰 충전 계산 (시간 차이 * 충전 속도)
-- delta는 지난 시간(ms) / 1000 * rate
local delta = math.max(0, now - last_refilled)
local filled_tokens = math.min(capacity, last_tokens + (delta * rate / 1000))

-- 요청 수락 여부 판단
local allowed = false
if filled_tokens >= requested then
  allowed = true
  filled_tokens = filled_tokens - requested
end

-- 상태 업데이트
redis.call("HSET", key, "tokens", filled_tokens, "last_refilled", now)
-- 키 만료 시간 설정 (메모리 관리, 예를 들어 1시간 동안 활동 없으면 삭제)
redis.call("EXPIRE", key, 3600)

return allowed