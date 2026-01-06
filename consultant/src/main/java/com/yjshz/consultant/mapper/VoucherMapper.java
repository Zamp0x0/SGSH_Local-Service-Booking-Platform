/*
 * @Author: liangchaaaaa git
 * @Date: 2025-10-18 19:19:38
 * @LastEditors: liangchaaaaa git
 * @LastEditTime: 2025-10-18 21:09:56
 * @FilePath: \YJSHZ\backend\consultant\src\main\java\com\yjshz\consultant\mapper\VoucherMapper.java
 * @Description: 这是默认设置,请设置`customMade`, 打开koroFileHeader查看配置 进行设置: https://github.com/OBKoro1/koro1FileHeader/wiki/%E9%85%8D%E7%BD%AE
 */
package com.yjshz.consultant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yjshz.consultant.pojo.Shop;
import com.yjshz.consultant.pojo.Voucher;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;


@Mapper
public interface VoucherMapper extends BaseMapper<Voucher> {

    @Select("select * from tb_voucher where shop_id=#{shopId}")
    List<Voucher> findVoucherByShopId(Long shopId);

    /*@Select("<script>" +
            "SELECT * FROM tb_voucher " +
            "WHERE id IN " +
            "<foreach item='id' collection='ids' open='(' separator=',' close=')'>" +
            "   #{id}" +
            "</foreach>" +
            "</script>")*/
    @Select("select * from tb_voucher where id=#{id}")
    Voucher findByIds(Long id);
}
