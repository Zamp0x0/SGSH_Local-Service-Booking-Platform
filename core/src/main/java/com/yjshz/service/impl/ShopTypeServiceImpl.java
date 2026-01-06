package com.yjshz.service.impl;

import cn.hutool.json.JSON;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yjshz.dto.Result;
import com.yjshz.entity.ShopType;
import com.yjshz.mapper.ShopTypeMapper;
import com.yjshz.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yjshz.utils.RedisConstants;
import com.yjshz.utils.RegexUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;


@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryList() {
        String cacheShopKey = RedisConstants.CACHE_SHOP_KEY+"list";
        List<String> itemList = stringRedisTemplate.opsForList().range(cacheShopKey, 0, -1);
        if (itemList != null && !itemList.isEmpty() ) {
            List<ShopType>typeList = itemList.stream()
                    .map(j -> JSONUtil.toBean(j,ShopType.class))
                    .collect(Collectors.toList());
            return Result.ok(typeList);
        }

        /*缓存未命中*/
        List<ShopType> dbList = this.list(new LambdaQueryWrapper<ShopType>().orderByAsc(ShopType::getSort));
        if( dbList == null || dbList.isEmpty()){
            return Result.fail("获取店铺列表失败");
        }

        List<String> jsonArr = dbList.stream().map(JSONUtil::toJsonStr).collect(Collectors.toList());
        Long status = stringRedisTemplate.opsForList().rightPushAll(cacheShopKey, jsonArr);
        System.out.println("操作状态"+status);
        stringRedisTemplate.expire(cacheShopKey,RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);

        return Result.ok(dbList);
    }
}
