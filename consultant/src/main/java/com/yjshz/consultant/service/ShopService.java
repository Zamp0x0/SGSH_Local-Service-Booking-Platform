package com.yjshz.consultant.service;

import com.yjshz.consultant.mapper.ShopMapper;
import com.yjshz.consultant.pojo.Shop;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ShopService {

    @Autowired
    private ShopMapper shopMapper;

    // 1️⃣ 精确查（AI 在用户确认了完整商家名后用）
    public Shop findShop(String shopName) {
        return shopMapper.findShop(shopName);
    }

    // 2️⃣ 模糊查（AI 用来给用户列候选）
    public List<Shop> searchByKeyword(String keyword) {
        return shopMapper.searchCandidates(keyword);   // ✅ 对齐 Mapper 里的方法名
    }
}
