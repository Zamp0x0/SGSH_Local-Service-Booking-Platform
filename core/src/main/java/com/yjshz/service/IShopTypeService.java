package com.yjshz.service;

import com.yjshz.dto.Result;
import com.yjshz.entity.ShopType;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;


public interface IShopTypeService extends IService<ShopType> {

    public Result queryList();

}
