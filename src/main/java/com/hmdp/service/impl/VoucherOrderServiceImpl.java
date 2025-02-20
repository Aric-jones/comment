package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.Voucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Autowired
    private ISeckillVoucherService seckillVoucherService;

    @Autowired
    private RedisIdWorker redisIdWorker;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RedissonClient redissonClient;

    /**
     * 秒杀优惠券
     *
     * @param voucherId
     * @return
     */
    @Override
    public Result seckillVoucher(Long voucherId) {

        // 1. 获取优惠券信息
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);

        // 2. 判断秒杀是否开始
        if (seckillVoucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("秒杀尚未开始");
        }

        // 3. 判断秒杀是否已经结束
        if (seckillVoucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("秒杀已经结束");
        }

        // 4. 判断库存是否充足
        if (seckillVoucher.getStock() < 1) {
            return Result.fail("库存不足");
        }

        // 使用用户的id作为锁的键，不同的用户不同的锁
        // user.getId().toString()虽然值是一样的都是用户的id，使用intern()，避免了在堆内存中创建新的对象


        // 动态代理负责维护事务的生命周期，包括事务的开启，提交，回滚
        // 如果需要使用目标对象，Spring无法再方法调用前后执行必要的事务管理功能，因为它依赖于代理机制来插入代码，因此需要使用AopContext获取代理对象。
        IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
        return proxy.createVoucherOrder(voucherId);

    }

    @Transactional
    public Result createVoucherOrder(Long voucherId) {

        UserDTO user = UserHolder.getUser();

        // 创建锁
        // SimpleRedisLock lock = new SimpleRedisLock("lock:order:" + userId, redisTemplate);
        RLock lock = redissonClient.getLock("lock:order:" + user.getId());

        // 创建是否成功，三个参数一个是重试时间，一个是过期时间，一个是过期时间单位
        boolean isLock = lock.tryLock();
        if (!isLock) {
            Result.fail("一个用户只能下单一次");
        }


        try {
            // 5. 判断用户是否已经购买过
            if (this.query().eq("user_id", user.getId()).eq("voucher_id", voucherId).count() > 0) {
                return Result.fail("该优惠卷只能购买一次");
            }


            // 6. 扣减库存
            boolean isEnough = seckillVoucherService.update()
                    .setSql("stock = stock - 1")
                    .eq("voucher_id", voucherId)
                    .gt("stock", 0)
                    .update();
            if (!isEnough) {
                return Result.fail("库存不足");
            }

            // 7. 创建订单
            VoucherOrder voucherOrder = new VoucherOrder();
            long voucherOrderId = redisIdWorker.nextId("order");
            voucherOrder.setVoucherId(voucherId);
            voucherOrder.setUserId(user.getId());
            voucherOrder.setId(voucherOrderId);
            this.save(voucherOrder);


            // 8. 返回订单id
            return Result.ok(voucherOrderId);
        } finally {
            lock.unlock();
        }
    }
}
