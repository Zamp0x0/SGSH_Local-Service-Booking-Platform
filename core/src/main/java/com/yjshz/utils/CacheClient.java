package com.yjshz.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSON;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.yjshz.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/*
* 方法1:将任意Java对象序列化为json并存储在string类型的key中，并且可以设置TTL过期时间
* 方法2:将任意Java对象序列化为json并存储在string类型的key中，并且可以设置逻辑过期时间，用于处理缓存击穿问题
* 方法3:根据指定的key查询缓存，并反序列化为指定类型，利用缓存空值的方式解决缓存穿透问题
* 方法4:根据指定的key查询缓存，并反序列化为指定类型，需要利用逻辑过期解决缓存击穿问题
*/
@Slf4j
@Component
public class CacheClient {

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
    private final StringRedisTemplate stringRedisTemplate;

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }
    public void set(String key, Object object, Long time, TimeUnit unit){
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(object),time,unit);
    }

    public void set(String key, Object object, Long expireSeconds){
        RedisData redisData = new RedisData();
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        redisData.setData(object);
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(object));
    }

    public <R,ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R>type, Function<ID,R>dbFallback,Long time,TimeUnit unit){
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);

        if(StrUtil.isNotBlank(json)){
            return JSONUtil.toBean(json,type);
        }

        if(json != null){
            return null;
        }

        R r = dbFallback.apply(id);
        if(r == null){
            //空值写入redis
            stringRedisTemplate.opsForValue().set(key,"",RedisConstants.CACHE_NULL_TTL,TimeUnit.MINUTES);
            return null;
        }
        //存在则写入redis
        this.set(key,r,time,unit);

        return r;
    }

    public <R,ID> R queryWithLogicalExpire(String keyPrefix,ID id,Class<R>type,Function<ID,R>dbFallback,Long time,TimeUnit unit){
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);

        if ( StrUtil.isBlank(json)) {
            return null;
        }

        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        JSONObject data = (JSONObject)redisData.getData();
        R r = JSONUtil.toBean(data, type);
        LocalDateTime expireTime = redisData.getExpireTime();

        if(expireTime.isAfter(LocalDateTime.now())){ //没过期
            return r;
        }

        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);

        if(isLock) {
            //开启独立线程，执行缓存重建
            CACHE_REBUILD_EXECUTOR.submit( () -> {
                try {
                    //查询数据库
                    R r1 = dbFallback.apply(id);
                    //写入redis
                    this.setWithLogicalExpire(key,r1,time,unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    unlock(lockKey);
                }
            });
        }

        return r;
    }

    /**
     * 将数据加入Redis，并设置逻辑过期时间
     */
    private  void setWithLogicalExpire(String key, Object value, Long timeout, TimeUnit unit) {
        RedisData redisData = new RedisData();
        redisData.setData(value);
        // unit.toSeconds()是为了确保计时单位是秒;
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(timeout)));
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(redisData));
    }

    private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", RedisConstants.LOCK_SHOP_TTL, TimeUnit.MINUTES);
        return BooleanUtil.isTrue(flag);
    }

    private boolean unlock(String key){
        Boolean flag = stringRedisTemplate.delete(key);
        return  BooleanUtil.isTrue(flag);

    }

}
