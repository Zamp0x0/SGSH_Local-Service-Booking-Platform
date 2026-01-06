package com.yjshz.controller;


import com.yjshz.dto.Result;
import com.yjshz.entity.SeckillVoucher;
import com.yjshz.entity.VoucherOrder;
import com.yjshz.service.ISeckillVoucherService;
import com.yjshz.service.IVoucherOrderService;
import com.yjshz.utils.RedisConstants;
import com.yjshz.utils.RedisIDWorker;
import com.yjshz.utils.UserHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.time.LocalDateTime;


@RestController
@RequestMapping("/voucher-order")
public class VoucherOrderController {

    @Resource
    private IVoucherOrderService voucherOrderService;

    @PostMapping("seckill/{id}")
    public Result seckillVoucher(@PathVariable("id") Long voucherId) {
        return voucherOrderService.seckillVoucher(voucherId);
    }


}
