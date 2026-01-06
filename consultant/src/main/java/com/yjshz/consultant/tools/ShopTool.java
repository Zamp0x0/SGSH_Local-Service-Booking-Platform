package com.yjshz.consultant.tools;

import com.yjshz.consultant.pojo.Shop;
import com.yjshz.consultant.service.ShopService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class ShopTool {

    private final ShopService shopService;

    public ShopTool(ShopService shopService) {
        this.shopService = shopService;
    }

    // 1️⃣ 给 AI 用的“候选搜索”
    @Tool("根据关键词搜索可能的商家候选名称，返回最多5个商家全称，让用户确认")
    public List<String> searchShopCandidates(@P("商家关键词") String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return Collections.emptyList();
        }

        List<Shop> list = shopService.searchByKeyword(keyword.trim());
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }

        return list.stream()
                .map(Shop::getName)
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .limit(5)
                .collect(Collectors.toList());
    }

    // 2️⃣ 精确查：改成返回 String，防止模型自己“包装成环境图片/点击查看”等输出
    @Tool("根据商家全称精确查询商家信息（返回纯文本，不要额外包装）")
    public String findShop(@P("商家全称") String shopName) {

        if (shopName == null || shopName.trim().isEmpty()) {
            return "商家名称不能为空，请提供商家全称。";
        }

        Shop shop = shopService.findShop(shopName.trim());
        if (shop == null) {
            return "未找到该商家信息，请确认商家全称是否完全一致（一个字不对也会查不到）。";
        }

        StringBuilder sb = new StringBuilder();

        sb.append("【").append(ns(shop.getName())).append("】\n");

        sb.append("📍 区域：").append(ns(shop.getArea())).append("\n");
        sb.append("🏠 地址：").append(ns(shop.getAddress())).append("\n");

        if (shop.getOpenHours() != null) {
            sb.append("⏰ 营业时间：").append(shop.getOpenHours()).append("\n");
        }

        if (shop.getAvgPrice() != null) {
            sb.append("💰 人均消费：").append(shop.getAvgPrice()).append(" 元\n");
        }

        if (shop.getScore() != null) {
            sb.append("⭐ 评分：").append(shop.getScore()).append(" 分\n");
        }

        if (shop.getSold() != null) {
            sb.append("🔥 已售：").append(shop.getSold()).append(" 单\n");
        }

        if (shop.getComments() != null) {
            sb.append("💬 评论数：").append(shop.getComments()).append("\n");
        }

        return sb.toString().trim();
    }

    private static String ns(String s) {
        return s == null ? "" : s.trim();
    }


    private static String nullSafe(String s) {
        return (s == null) ? "" : s.trim();
    }
}
