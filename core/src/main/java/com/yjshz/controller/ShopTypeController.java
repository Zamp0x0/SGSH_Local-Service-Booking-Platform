package com.yjshz.controller;


import com.yjshz.dto.Result;
import com.yjshz.entity.ShopType;
import com.yjshz.service.IShopTypeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/shop-type")
public class ShopTypeController {
    @Resource
    private IShopTypeService typeService;

    @GetMapping("list")
    public Result queryList() {
        List<ShopType> typeList = (List<ShopType>)typeService.queryList().getData();
        return Result.ok(typeList);
    }
}
