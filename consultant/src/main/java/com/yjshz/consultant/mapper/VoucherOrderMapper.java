package com.yjshz.consultant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yjshz.consultant.pojo.Voucher;
import com.yjshz.consultant.pojo.VoucherOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;


@Mapper
public interface VoucherOrderMapper extends BaseMapper<VoucherOrder> {

    @Select("select voucher_id from tb_voucher_order where phone = #{phone}")
    List<Long> findByPhone(String phone);
}
