---@diagnostic disable: undefined-global
---
--- Created by 17926.
--- DateTime: 2025/9/8 21:41
---

local key = KEYS[1]; --锁的key
local threadId = ARGV[1]; --线程唯一标识
local releaseTime = ARGV[2]; --锁的自动释放时间

-- 判断当前锁是否还是自己持有
if( redis.call('hexist',key,threadId) == 0) then
    return nil;
end;
--是当前线程的锁，可重入次数-1
local count = redis.call('hincreby',key,threadId,'-1');
if( count > 0) then
    -- 仍不能释放
    redis.call('expire',key,releaseTime);
    return nil;
else
    redis.call('del',key);
    return nil;
end;

