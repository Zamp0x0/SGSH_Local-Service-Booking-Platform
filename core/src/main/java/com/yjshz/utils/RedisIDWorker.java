package com.yjshz.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIDWorker {

    private static final long BEGIN_TIMESTAMP = 1640995200;
    /**
     * 序列号的位数
     * */
    private static final long COUNT_BITS = 32;

    public void setStringRedisTemplate(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Resource
    private StringRedisTemplate stringRedisTemplate;


    public long nextId(String keyPrefix){
        //生成时间戳
        LocalDateTime now = LocalDateTime.now();//取得系统当前本地时间（不带时区）
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);//把本地时间当成 UTC 来算绝对秒数
        long timestamp = nowSecond - BEGIN_TIMESTAMP;

        //生成序列号
        //以当天的时间戳为key，防止一直自增下去导致超时，这样每天的极限都是 2^{31}
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        //自增长
        Long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);

        return timestamp << COUNT_BITS | count;
    }



}
