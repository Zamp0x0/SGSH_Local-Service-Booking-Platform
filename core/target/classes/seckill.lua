-- 入参
-- ARGV[1] = voucherId
-- ARGV[2] = userId
-- ARGV[3] = orderId

local voucherId = ARGV[1]
local userId = ARGV[2]
local orderId = ARGV[3]

local stockKey = 'seckill:stock:' .. voucherId
local orderKey = 'seckill:order:' .. voucherId
local streamKey = 'stream.orders'

-- 1) 取库存（要兜底 nil）
local stock = tonumber(redis.call('get', stockKey))
if (stock == nil) then
    -- 未预热 / key不存在
    return 1
end

-- 2) 库存不足
if (stock <= 0) then
    return 1
end

-- 3) 是否重复下单
if (redis.call('sismember', orderKey, userId) == 1) then
    return 2
end

-- 4) 扣库存
redis.call('decr', stockKey)

-- 5) 记录已下单用户
redis.call('sadd', orderKey, userId)

-- 6) ★ 写入 Redis Stream（这是你现在最缺的）
redis.call('xadd', streamKey, '*',
        'id', orderId,
        'userId', userId,
        'voucherId', voucherId
)

return 0
