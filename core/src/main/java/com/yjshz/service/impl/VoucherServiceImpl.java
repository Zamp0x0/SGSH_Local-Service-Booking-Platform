package com.yjshz.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yjshz.dto.Result;
import com.yjshz.entity.SeckillVoucher;
import com.yjshz.entity.Voucher;
import com.yjshz.mapper.VoucherMapper;
import com.yjshz.service.ISeckillVoucherService;
import com.yjshz.service.IVoucherService;
import com.yjshz.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.List;

@Service
public class VoucherServiceImpl extends ServiceImpl<VoucherMapper, Voucher> implements IVoucherService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryVoucherOfShop(Long shopId) {
        // mapper 通常是 voucher left join seckill_voucher
        List<Voucher> vouchers = getBaseMapper().queryVoucherOfShop(shopId);
        return Result.ok(vouchers);
    }

    /**
     * 新增秒杀券/普通券：
     * - 必写 tb_voucher
     * - 如果是秒杀券(type=1)：再写 tb_seckill_voucher + 预热 Redis 库存
     */
    @Override
    @Transactional
    public void addSeckillVoucher(Voucher voucher) {
        // 1) 先写 voucher 主表
        save(voucher);

        // 2) 只有 type=1 才写秒杀表 + Redis
        if (voucher.getType() != null && Integer.valueOf(1).equals(voucher.getType())) {

            // 2.1 写秒杀表
            SeckillVoucher seckillVoucher = new SeckillVoucher();
            seckillVoucher.setVoucherId(voucher.getId());
            seckillVoucher.setStock(voucher.getStock());
            seckillVoucher.setBeginTime(voucher.getBeginTime());
            seckillVoucher.setEndTime(voucher.getEndTime());
            seckillVoucherService.save(seckillVoucher);

            // 2.2 预热 Redis 库存（Lua 扣减用的就是它）
            String stockKey = RedisConstants.SECKILL_STOCK_KEY + voucher.getId();
            stringRedisTemplate.opsForValue().set(stockKey, String.valueOf(voucher.getStock()));
        }
    }
}
