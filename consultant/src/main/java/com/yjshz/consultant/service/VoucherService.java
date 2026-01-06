package com.yjshz.consultant.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.yjshz.consultant.mapper.ShopMapper;
import com.yjshz.consultant.mapper.VoucherMapper;
import com.yjshz.consultant.mapper.VoucherOrderMapper;
import com.yjshz.consultant.pojo.Shop;
import com.yjshz.consultant.pojo.Voucher;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;


import java.util.ArrayList;
import java.util.List;

@Service
public class VoucherService {

    @Autowired
    private VoucherMapper voucherMapper;

    @Autowired
    private VoucherOrderMapper voucherOrderMapper;

    @Autowired
    private ShopMapper shopMapper;

    //1.查询商家信息
    public List<Voucher> findVoucherByShopName(String shopName) {
        Shop shop = shopMapper.findShop(shopName);
        return voucherMapper.findVoucherByShopId(shop.getId());
    }

    //2.查询用户拥有的优惠券
    public List<Voucher> findVoucherByUserPhone(String userPhone) {
        List<Long> voucherIds = voucherOrderMapper.findByPhone(userPhone);
        String ids = StringUtils.join(voucherIds, ",");
        // System.out.println("用户拥有的优惠券id是: " + ids);
        List<Voucher> vouchers = new ArrayList<>();
        for (Long voucherId : voucherIds) {
            vouchers.add(voucherMapper.findByIds(voucherId));
        }
        // System.out.println("查询用户拥有的优惠券: " + vouchers);
        return vouchers;
    }
}