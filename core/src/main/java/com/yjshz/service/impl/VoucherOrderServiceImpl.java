package com.yjshz.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yjshz.dto.Result;
import com.yjshz.entity.VoucherOrder;
import com.yjshz.mapper.VoucherOrderMapper;
import com.yjshz.service.ISeckillVoucherService;
import com.yjshz.service.IVoucherOrderService;
import com.yjshz.utils.RedisIDWorker;
import com.yjshz.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource private RedisIDWorker redisIDWorker;
    @Resource private ISeckillVoucherService seckillVoucherService;
    @Resource private StringRedisTemplate stringRedisTemplate;
    @Resource private RedissonClient redissonClient;

    // ✅ 延迟获取事务代理：避免@PostConstruct阶段“取自己”导致循环依赖
    @Resource
    private ObjectProvider<IVoucherOrderService> voucherOrderServiceProvider;

    // Lua脚本
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    // Stream 配置（跟你老版本一致）
    private static final String STREAM_KEY = "stream.orders";
    private static final String GROUP_NAME = "g1";
    private static final String CONSUMER_NAME = "c1";

    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();
    private final AtomicBoolean running = new AtomicBoolean(true);

    @PostConstruct
    private void init() {
        // 1) 确保 stream + group 存在（避免 NOGROUP）
        try {
            // stream存在但group不存在 -> 创建group
            stringRedisTemplate.opsForStream().createGroup(STREAM_KEY, ReadOffset.from("0"), GROUP_NAME);
        } catch (Exception e) {
            // 可能是：stream不存在 或 group已存在
            try {
                // 如果 stream 不存在：先 XADD 一个初始化消息创建 stream，然后再创建 group
                stringRedisTemplate.opsForStream().add(STREAM_KEY, Collections.singletonMap("init", "1"));
                stringRedisTemplate.opsForStream().createGroup(STREAM_KEY, ReadOffset.from("0"), GROUP_NAME);
            } catch (Exception ignore) {
                // group 已存在等情况直接忽略
            }
        }

        // 2) 启动后台线程：消费 stream
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }

    @PreDestroy
    private void shutdown() {
        running.set(false);
        SECKILL_ORDER_EXECUTOR.shutdownNow();
    }

    /**
     * 同步接口：Lua校验 + 生成orderId + 由Lua写入Stream + 返回订单id
     */
    @Override
    public Result seckillVoucher(Long voucherId) {
        Long userId = UserHolder.getUser().getId();

        // ✅ orderId 要先生成，传给Lua，让Lua把订单写进stream（老逻辑就是这样）
        long orderId = redisIDWorker.nextId("order");

        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),
                userId.toString(),
                String.valueOf(orderId)
        );

        if (result == null) return Result.fail("系统繁忙，请稍后重试");

        int r = result.intValue();
        if (r == 1) return Result.fail("库存不足/未预热");
        if (r == 2) return Result.fail("不能重复下单");
        if (r != 0) return Result.fail("下单失败");

        return Result.ok(orderId);
    }

    /**
     * Stream消费者：读取 -> 落库 -> ACK；异常 -> 处理pending-list
     */
    private class VoucherOrderHandler implements Runnable {
        @Override
        public void run() {
            while (running.get() && !Thread.currentThread().isInterrupted()) {
                try {
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from(GROUP_NAME, CONSUMER_NAME),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create(STREAM_KEY, ReadOffset.lastConsumed())
                    );

                    if (list == null || list.isEmpty()) continue;

                    MapRecord<String, Object, Object> record = list.get(0);
                    VoucherOrder voucherOrder = toVoucherOrder(record);

                    // 脏消息（比如 init）直接ACK清理
                    if (voucherOrder == null) {
                        stringRedisTemplate.opsForStream().acknowledge(STREAM_KEY, GROUP_NAME, record.getId());
                        continue;
                    }

                    // ✅ 关键：通过代理调用事务方法
                    IVoucherOrderService proxy = voucherOrderServiceProvider.getObject();
                    proxy.createVoucherOrder(voucherOrder);

                    // ACK确认
                    stringRedisTemplate.opsForStream().acknowledge(STREAM_KEY, GROUP_NAME, record.getId());

                } catch (IllegalStateException e) {
                    // 通常是应用关闭/重启，Redis连接工厂销毁
                    log.warn("Redis连接不可用（应用可能在关闭/重启），订单处理线程退出");
                    break;
                } catch (Exception e) {
                    log.error("处理订单异常，准备处理pending-list", e);
                    try {
                        handlePendingList();
                    } catch (Exception ex) {
                        log.error("处理pending-list异常", ex);
                    }
                    // 防刷屏
                    sleepQuietly(50);
                }
            }
        }

        private void handlePendingList() {
            while (running.get() && !Thread.currentThread().isInterrupted()) {
                List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                        Consumer.from(GROUP_NAME, CONSUMER_NAME),
                        StreamReadOptions.empty().count(1),
                        StreamOffset.create(STREAM_KEY, ReadOffset.from("0"))
                );

                if (list == null || list.isEmpty()) break;

                MapRecord<String, Object, Object> record = list.get(0);
                VoucherOrder voucherOrder = toVoucherOrder(record);

                if (voucherOrder == null) {
                    stringRedisTemplate.opsForStream().acknowledge(STREAM_KEY, GROUP_NAME, record.getId());
                    continue;
                }

                try {
                    IVoucherOrderService proxy = voucherOrderServiceProvider.getObject();
                    proxy.createVoucherOrder(voucherOrder);
                    stringRedisTemplate.opsForStream().acknowledge(STREAM_KEY, GROUP_NAME, record.getId());
                } catch (Exception e) {
                    log.error("处理pending订单异常", e);
                    sleepQuietly(50);
                }
            }
        }
    }

    private VoucherOrder toVoucherOrder(MapRecord<String, Object, Object> record) {
        Map<Object, Object> value = record.getValue();
        VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);

        // ✅ 字段校验：避免 init/脏消息导致空指针/乱入库
        if (voucherOrder.getId() == null
                || voucherOrder.getUserId() == null
                || voucherOrder.getVoucherId() == null) {
            log.warn("订单字段缺失，直接ACK跳过: recordId={}, value={}", record.getId(), value);
            return null;
        }
        return voucherOrder;
    }

    private void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 事务：1) Redisson防重（兜底） 2) 防重复下单 3) 扣MySQL库存 4) 保存订单
     */
    @Override
    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        Long voucherId = voucherOrder.getVoucherId();

        // ✅ 分布式锁：同一个用户并发下单兜底
        RLock lock = redissonClient.getLock("order:" + userId);
        boolean isLock = lock.tryLock();
        if (!isLock) {
            log.warn("不允许重复下单(抢锁失败), userId={}", userId);
            return;
        }

        try {
            // 1) 一人一单校验
            Integer count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
            if (count != null && count > 0) {
                log.warn("该用户已经购买过一次, userId={}, voucherId={}", userId, voucherId);
                return;
            }

            // 2) 扣减 MySQL 库存（乐观条件：stock > 0）
            boolean success = seckillVoucherService.update()
                    .setSql("stock = stock - 1")
                    .eq("voucher_id", voucherId)
                    .gt("stock", 0)
                    .update();

            if (!success) {
                log.warn("扣减MySQL库存失败, voucherId={}", voucherId);
                return;
            }

            // 3) 保存订单
            save(voucherOrder);

        } finally {
            lock.unlock();
        }
    }
}
