---
--- Created by 17926.
--- DateTime: 2025/9/10 10:44
---

local key = KEYS[1]; --锁的key
local threadId = ARGV[1]; --线程唯一标识
local releaseTime = ARGV[2]; --锁的自动释放时间
-- 锁是否存在
if(redis.call('exist',key) == 0) then
    --不存在，申请锁
    redis.call('hset',key,threadId,'1');
    redis.call('expire',key,releaseTime);
    return 1;
end;

-- 锁已经存在，判断threadId是否是自己线程的
if(redis.call('hexist',key,threadId) == 1) then
    -- 锁存在，重入次数+1
    redis.call('hincrby',key,threadId,'1');
    redis.call('expire',key,releaseTime);
    return 1;
end;
return 0;
