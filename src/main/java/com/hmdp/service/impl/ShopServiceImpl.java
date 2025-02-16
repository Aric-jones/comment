package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    /**
     * 根据id查询店铺信息
     *
     * @param id
     * @return
     */
    @Override
    public Object queryById(Long id) {
        String shopKey = RedisConstants.CACHE_SHOP_KEY + id;
        // 1. 查询缓存中是否有店铺信息
        String shopValue = redisTemplate.opsForValue().get(shopKey);

        // 2. 判断是否存在
        if(StringUtils.isNotBlank(shopValue)){
            // 3. 存在直接返回
            return Result.ok(JSONUtil.toBean(shopValue, Shop.class));
        }

        // 4. 不存在，根据id查询数据库
        Shop shop = this.getById(id);

        // 5. 查询不到数据
        if(Objects.isNull(shop)){
            return Result.fail("店铺不存在");
        }

        // 6. 将查询到的数据保存到redis中
        redisTemplate.opsForValue().set(shopKey, JSONUtil.toJsonStr(shop), RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);

        // 7. 返回
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
}
