package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * @ClassName: SimpleRedisLock
 * @Description: 初级的redis分布式锁
 * @Author: csh
 * @Date: 2025-02-19 15:05
 */
public class SimpleRedisLock implements ILock{
    private StringRedisTemplate stringRedisTemplate;
    private String name;
    private static final String KEY_PREFIX = "lock:";
    private static final String UUID_PREFIX = UUID.randomUUID().toString(true)+"-";
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    static{
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }


    public SimpleRedisLock(String name , StringRedisTemplate stringRedisTemplate ) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.name = name;
    }


    /**
     * @param timeoutSec
     * @description: 尝试获取锁
     * @param: timeoutSec 锁过期时间
     * @return: boolean
     * @author: csh
     * @date: 2025/2/19
     */
    @Override
    public boolean tryLock(long timeoutSec) {

        // 获取线程提示
        String threadId = UUID_PREFIX + Thread.currentThread().getId();

        // 获取线程锁
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + name, threadId, timeoutSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(success);
    }

    /**
     * @description: 释放锁
     * @param:
     * @return: void
     * @author: csh
     * @date: 2025/2/19
     */
    @Override
    public void unlock() {
        // 调用lua脚本
        stringRedisTemplate.execute(
                UNLOCK_SCRIPT,
                // 获取锁的key
                Collections.singletonList(KEY_PREFIX + name),
                // 获取线程id
                UUID_PREFIX + Thread.currentThread().getId()
        );
    }

//    @Override
//    public void unlock() {
//        // 判断此时的锁是不是自己的
//        String threadId = UUID_PREFIX + Thread.currentThread().getId();
//        String nowThreadId = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);
//        if(threadId.equals(nowThreadId)){
//            // 释放锁
//            stringRedisTemplate.delete(KEY_PREFIX + name);
//        }
//    }
}
