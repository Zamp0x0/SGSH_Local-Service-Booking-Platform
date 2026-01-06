---
--- Created by 17926.
--- DateTime: 2025/10/20 15:57
---

-- KEYS[1]: 存储用户ID的集合（如 blog:liked:123）
-- KEYS[2]: 存储点赞数的计数器（如 blog:liked:count:123）
-- ARGV[1]: 用户ID
local key = KEYS[1]
local countKey = KEYS[2]
local user = ARGV[1]


if redis.call('SISMEMBER',key,user) == 0 then
    redis.call('SADD',key,user)
    redis.call('INCR',countKey)
    return 1
else
    redis.call('SREM',key,user)
    redis.call('DECR',countKey)
    return 0
end
