package com.yjshz.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.yjshz.dto.Result;
import com.yjshz.entity.Shop;
import com.yjshz.mapper.ShopMapper;
import com.yjshz.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yjshz.utils.CacheClient;
import com.yjshz.utils.RedisConstants;
import com.yjshz.utils.RedisData;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    @Override
    public Result queryById(Long id) {
        // ✅ 互斥锁版本
        Shop shop = queryWithMutex(id);

        // 也可以切换成你 CacheClient 的版本（你以后想统一写法就用这个）
        // Shop shop = cacheClient.queryWithMutex(
        //         RedisConstants.CACHE_SHOP_KEY, id, Shop.class, this::getById,
        //         RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES
        // );

        if (shop == null) {
            return Result.fail("查询失败");
        }
        return Result.ok(shop);
    }

    /**
     * ✅ 互斥锁解决缓存击穿（修复：没拿到锁不会误删别人的锁；拿到锁后会二次检查；写缓存带TTL）
     */
    public Shop queryWithMutex(Long id) {
        String key = RedisConstants.CACHE_SHOP_KEY + id;

        // 1) 查缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(shopJson)) {
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        // 命中空值（防穿透）
        if (shopJson != null) {
            return null;
        }

        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        boolean isLock = false;

        try {
            // 2) 抢锁
            isLock = tryLock(lockKey);
            if (!isLock) {
                Thread.sleep(50);
                return queryWithMutex(id);
            }

            // 3) 抢到锁后：二次检查缓存（很关键，避免重复查库）
            shopJson = stringRedisTemplate.opsForValue().get(key);
            if (StrUtil.isNotBlank(shopJson)) {
                return JSONUtil.toBean(shopJson, Shop.class);
            }
            if (shopJson != null) {
                return null;
            }

            // 4) 查数据库
            Shop shop = getById(id);

            // 5) 数据库不存在：写空值
            if (shop == null) {
                stringRedisTemplate.opsForValue().set(
                        key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES
                );
                return null;
            }

            // 6) 写入缓存 + TTL（别忘了TTL）
            stringRedisTemplate.opsForValue().set(
                    key,
                    JSONUtil.toJsonStr(shop),
                    RedisConstants.CACHE_SHOP_TTL,
                    TimeUnit.MINUTES
            );

            return shop;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } finally {
            // 7) 只有拿到锁的人才释放锁（避免误删别人的锁）
            if (isLock) {
                unlock(lockKey);
            }
        }
    }

    /**
     * 解决缓存穿透（保留你的写法，但也补上写缓存TTL更规范）
     */
    public Shop queryWithPassThrough(Long id) {
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);

        if (StrUtil.isNotBlank(shopJson)) {
            return JSONUtil.toBean(shopJson, Shop.class);
        }

        if (shopJson != null) {
            return null;
        }

        Shop shop = getById(id);
        if (shop == null) {
            stringRedisTemplate.opsForValue().set(
                    key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES
            );
            return null;
        }

        // ✅ 写缓存带TTL
        stringRedisTemplate.opsForValue().set(
                key, JSONUtil.toJsonStr(shop),
                RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES
        );
        return shop;
    }

    /**
     * 逻辑过期（你原来叫 queryWithLogincalExpire，我保留名字但建议你改拼写）
     */
    public Shop queryWithLogincalExpire(Long id) {
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);

        if (StrUtil.isBlank(shopJson)) {
            return null;
        }

        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        JSONObject data = (JSONObject) redisData.getData();
        Shop shop = JSONUtil.toBean(data, Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();

        // 没过期：直接返回
        if (expireTime.isAfter(LocalDateTime.now())) {
            return shop;
        }

        // 过期：尝试抢锁，异步重建
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);

        if (isLock) {
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    // 重建缓存（逻辑过期）
                    this.saveShop2Redis(id, 20L);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    unlock(lockKey);
                }
            });
        }

        // 返回旧数据
        return shop;
    }

    @Transactional
    @Override
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺id不存在");
        }
        updateById(shop);
        stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY + id);
        return Result.ok();
    }

    /**
     * 写入逻辑过期缓存
     */
    public void saveShop2Redis(Long id, Long expireSeconds) throws InterruptedException {
        Shop shop = getById(id);
        Thread.sleep(200); // 模拟重建延迟
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));

        String key = RedisConstants.CACHE_SHOP_KEY + id;
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(
                key, "1", RedisConstants.LOCK_SHOP_TTL, TimeUnit.MINUTES
        );
        return BooleanUtil.isTrue(flag);
    }

    private boolean unlock(String key) {
        Boolean flag = stringRedisTemplate.delete(key);
        return BooleanUtil.isTrue(flag);
    }
}
