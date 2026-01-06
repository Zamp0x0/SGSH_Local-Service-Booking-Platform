package com.yjshz.service;

import com.yjshz.dto.Result;
import com.yjshz.entity.Shop;
import com.baomidou.mybatisplus.extension.service.IService;


public interface IShopService extends IService<Shop> {

    Result queryById(Long id);

    Result update(Shop shop);
}
