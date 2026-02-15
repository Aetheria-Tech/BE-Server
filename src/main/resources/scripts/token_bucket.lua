-- KEYS[1]: rate limit key
-- ARGV[1]: capacity
-- ARGV[2]: refill rate
-- ARGV[3]: requested tokens
-- ARGV[4]: current timestamp

local key = KEYS[1]

local capacity = tonumber(ARGV[1])
local rate = tonumber(ARGV[2])
local requested = tonumber(ARGV[3])
local now = tonumber(ARGV[4])

local last_tokens = tonumber(redis.call("HGET", key, "tokens"))
local last_refilled = tonumber(redis.call("HGET", key, "last_refilled"))

if last_tokens == nil then
  last_tokens = capacity
  last_refilled = now
end

local delta = math.max(0, now - last_refilled)
local filled_tokens = math.min(capacity, last_tokens + (delta * rate / 1000))

local allowed = false
if filled_tokens >= requested then
  allowed = true
  filled_tokens = filled_tokens - requested
end

-- [수정] HSET -> HMSET (여러 필드를 한 번에 저장하기 위해 변경)
redis.call("HMSET", key, "tokens", filled_tokens, "last_refilled", now)

redis.call("EXPIRE", key, 3600)

return allowed