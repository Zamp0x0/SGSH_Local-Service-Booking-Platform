package com.yjshz.service;

import com.yjshz.dto.Result;
import com.yjshz.entity.VoucherOrder;

public interface IVoucherOrderService {

    // 秒杀接口（同步：Lua + 入队）
    Result seckillVoucher(Long voucherId);

    // 异步落库接口（给代理 + @Transactional 用）
    void createVoucherOrder(VoucherOrder voucherOrder);
}
