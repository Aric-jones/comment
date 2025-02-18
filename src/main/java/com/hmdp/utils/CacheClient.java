package com.hmdp.utils;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * @ClassName: CacheClient
 * @Description: redis工具类
 * @Author: csh
 * @Date: 2025-02-16 22:40
 */

@Slf4j
@Component
public class CacheClient {

    private final StringRedisTemplate redisTemplate;

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.redisTemplate = stringRedisTemplate;
    }

    public void set(String key, Object value, Long time, TimeUnit unit) {
        redisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        redisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    public  <R,ID> R queryWhitPassThrough(String keyPrefix , ID id, Class<R> type, Function<ID,R> dbFallback, Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        // 1. 查询缓存中是否有店铺信息
        String json = redisTemplate.opsForValue().get(key);

        // 2. 判断是否存在
        if (StringUtils.isNotBlank(json)) {
            // 3. 存在直接返回
            return JSONUtil.toBean(json, type);
        }

        // 4. 判断是否是空对象
        if (RedisConstants.CACHE_NULL_VALUE.equals(json)) {
            return null;
        }

        // 5. 不存在，根据id查询数据库
        R r = dbFallback.apply(id);

        // 6. 查询不到数据
        if (Objects.isNull(r)) {
            // 缓存空对象，解决缓存穿透问题
            redisTemplate.opsForValue().set(key, RedisConstants.CACHE_NULL_VALUE, RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }

        // 7. 将查询到的数据保存到redis中
        this.set(key, r, time, unit);

        // 8. 返回
        return r;
    }

    public <R,ID> R queryWhitLogicalExpire(String keyPrefix ,String lockKeyPrefix, ID id, Class<R> type, Function<ID,R> dbFallback, Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        // 1. 查询缓存中是否有店铺信息
        String json = redisTemplate.opsForValue().get(key);

        // 2. 判断是否命中
        if (StringUtils.isBlank(json)) {
            // 3. 不存在直接返回
            return null;
        }

        // 4. 命中，将json字符串转换为对象
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(),type);

        LocalDateTime expireTime = redisData.getExpireTime();

        // 5. 判断是否过期
        if(expireTime.isAfter(LocalDateTime.now())){
            // 5.1 未过期直接返回数据
            return r;
        }

        // 5.2 过期需要重新创建

        // 6. 创建缓存数据

        // 7. 获取互斥锁
        String LockKey = lockKeyPrefix + id;
        boolean isLock = tryLock(LockKey);

        if(isLock){
            // 7.2 获取成功，开启线程查询数据，缓存数据，释放锁
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    // 查询数据库
                    R apply = dbFallback.apply(id);
                    // 写入redis
                    this.setWithLogicalExpire(key, apply, time, unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }finally {
                    unLock(LockKey);
                }
            });

        }

        // 8.返回数据
        return r;
    }

    private boolean tryLock(String key){
        Boolean isLock = redisTemplate.opsForValue().setIfAbsent(key, "1", RedisConstants.LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(isLock);
    }

    private void unLock(String key){
        redisTemplate.delete(key);
    }

}
