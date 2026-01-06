package com.yjshz.consultant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yjshz.consultant.pojo.Shop;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ShopMapper extends BaseMapper<Shop> {

    // 1️⃣ 精确匹配：必须商家全称完全一致（给 AI 最终确认用）
    @Select("select * from tb_shop where name = #{shopName} limit 1")
    Shop findShop(@Param("shopName") String shopName);

    // 2️⃣ 模糊匹配：给 AI 列候选商家（最多 5 个）
    @Select("""
            select *
            from tb_shop
            where name like concat('%', #{keyword}, '%')
            order by length(name) asc
            limit 5
            """)
    List<Shop> searchCandidates(@Param("keyword") String keyword);
}
