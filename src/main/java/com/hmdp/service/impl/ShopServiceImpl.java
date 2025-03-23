package com.hmdp.service.impl;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TTL;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private CacheClient cacheClient;

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    /**
     * 根据id查询店铺信息
     *
     * @param id
     * @return
     */
    @Override
    public Result queryById(Long id) {
        // 缓存穿透查询
        // Shop Shop = queryWhitPassThrough(id);
        // Shop shop = cacheClient.queryWhitPassThrough(RedisConstants.CACHE_SHOP_KEY, id, Shop.class, this::getById, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        // 互斥锁解决缓存击穿查询
        // Shop shop = queryWhitMutex(id);
        // 逻辑过期解决缓存击穿查询
        // Shop shop = queryWhitLogicalExpire(id);
        Shop shop = cacheClient
                .queryWithPassThrough(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        if(Objects.isNull(shop)){
            return Result.fail("店铺不存在");
        }
        // 8. 返回
        return Result.ok(shop);
    }

    /**
     * 更新店铺信息
     *
     * @param shop
     */
    @Override
    public void updateEntityById(Shop shop) {
        // 更新数据
        this.getById(shop.getId());

        // 删除缓存信息
        redisTemplate.delete(RedisConstants.CACHE_SHOP_KEY + shop.getId());
    }

    /**
     * @param typeId
     * @param current
     * @param x
     * @param y
     * @return com.hmdp.dto.Result
     * @Author:CSH
     * @Updator:CSH
     * @Date 2025/3/23 13:38
     * @Description:
     */
    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        return null;
    }

    private Shop queryWhitLogicalExpire(Long id) {
        String shopKey = RedisConstants.CACHE_SHOP_KEY + id;
        // 1. 查询缓存中是否有店铺信息
        String shopValue = redisTemplate.opsForValue().get(shopKey);

        // 2. 判断是否命中
        if (StringUtils.isBlank(shopValue)) {
            // 3. 不存在直接返回
            return null;
        }

        // 4. 命中，将json字符串转换为对象
        RedisData redisData = JSONUtil.toBean(shopValue, RedisData.class);
        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);

        LocalDateTime expireTime = redisData.getExpireTime();

        // 5. 判断是否过期
        if(expireTime.isAfter(LocalDateTime.now())){
            // 5.1 未过期直接返回数据
            return shop;
        }

        // 5.2 过期需要重新创建

        // 6. 创建缓存数据

        // 7. 获取互斥锁
        String LockKey = RedisConstants.LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(LockKey);

        if(isLock){
            // 7.2 获取成功，开启线程查询数据，缓存数据，释放锁
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    this.saveShop2Redis(id, 20L);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }finally {
                    unLock(LockKey);
                }
            });

        }

        // 8.返回数据
        return shop;
    }

    private Shop queryWhitMutex(Long id) {
        String shopKey = RedisConstants.CACHE_SHOP_KEY + id;
        String LockKey = RedisConstants.LOCK_SHOP_KEY + id;
        // 1. 查询缓存中是否有店铺信息
        String shopValue = redisTemplate.opsForValue().get(shopKey);

        // 2. 判断是否存在
        if (StringUtils.isNotBlank(shopValue)) {
            // 3. 存在直接返回
            return JSONUtil.toBean(shopValue, Shop.class);
        }

        // 4. 判断是否是空对象
        if (RedisConstants.CACHE_NULL_VALUE.equals(shopValue)) {
            return null;
        }
        Shop shop = new Shop();
        try {
            // 5. 尝试获取锁
            if (!tryLock(LockKey)) {
                // 5.1 获取锁失败，休眠重试
                Thread.sleep(50);
                return queryWhitMutex(id);
            }

            // 6. 不存在，根据id查询数据库
            shop = this.getById(id);

            // 7. 查询不到数据
            if (Objects.isNull(shop)) {
                // 缓存空对象，解决缓存穿透问题
                redisTemplate.opsForValue().set(shopKey, RedisConstants.CACHE_NULL_VALUE, RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }

            // 8. 将查询到的数据保存到redis中
            redisTemplate.opsForValue().set(shopKey, JSONUtil.toJsonStr(shop), RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            // 9. 释放锁
            unLock(LockKey);
        }

        return shop;
    }

    private Shop queryWhitPassThrough(Long id) {
        String shopKey = RedisConstants.CACHE_SHOP_KEY + id;
        // 1. 查询缓存中是否有店铺信息
        String shopValue = redisTemplate.opsForValue().get(shopKey);

        // 2. 判断是否存在
        if (StringUtils.isNotBlank(shopValue)) {
            // 3. 存在直接返回
            return JSONUtil.toBean(shopValue, Shop.class);
        }

        // 4. 判断是否是空对象
        if (RedisConstants.CACHE_NULL_VALUE.equals(shopValue)) {
            return null;
        }

        // 5. 不存在，根据id查询数据库
        Shop shop = this.getById(id);

        // 6. 查询不到数据
        if (Objects.isNull(shop)) {
            // 缓存空对象，解决缓存穿透问题
            redisTemplate.opsForValue().set(shopKey, RedisConstants.CACHE_NULL_VALUE, RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }

        // 7. 将查询到的数据保存到redis中
        redisTemplate.opsForValue().set(shopKey, JSONUtil.toJsonStr(shop), RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);

        // 8. 返回
        return shop;
    }

    public void saveShop2Redis(Long id, Long expireSeconds) throws InterruptedException {
        // 1. 查询店铺数据
        Shop shop = this.getById(id);
        Thread.sleep(200);

        // 2. 封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));

        // 3. 写入redis
        redisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY+id, JSONUtil.toJsonStr(redisData));
    }

    private boolean tryLock(String key){
        Boolean isLock = redisTemplate.opsForValue().setIfAbsent(key, "1", RedisConstants.LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(isLock);
    }

    private void unLock(String key){
        redisTemplate.delete(key);
    }



}
