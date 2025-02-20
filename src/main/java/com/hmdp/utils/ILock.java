package com.hmdp.utils;

/**
 * @ClassName: ILock
 * @Description: 简单redis分布式锁接口
 * @Author: csh
 * @Date: 2025-02-19 15:07
 */
public interface ILock {

    /**
     * @description: 尝试获取锁
     * @param: timeoutSec 锁过期时间
     * @return: boolean
     * @author: csh
     * @date: 2025/2/19
     */
    boolean tryLock(long timeoutSec);

    /**
     * @description: 释放锁
     * @param:
     * @return: void
     * @author: csh
     * @date: 2025/2/19
     */
    void unlock();
}
