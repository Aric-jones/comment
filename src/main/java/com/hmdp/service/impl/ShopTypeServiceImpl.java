package com.hmdp.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 查询所有店铺类型
     *
     * @return 店铺类型列表
     */
    @Override
    public Result queryTypeList() {
        String typeListKey = RedisConstants.CACHE_TYPE_LIST_KEY;
        // 1. 查询redis中有没有缓存数据
        List<String> shopTypeJson = stringRedisTemplate.opsForList().range(typeListKey, 0, -1);

        // 2. 如果有直接返回
        if(CollectionUtil.isNotEmpty(shopTypeJson)){
            List<ShopType> shopTypes = JSONUtil.toList(shopTypeJson.toString(), ShopType.class);
            shopTypes.sort(((o1, o2) -> o1.getSort() - o2.getSort()));
            return Result.ok(shopTypes);
        }

        // 3. 如果没有，查询数据库
        List<ShopType> shopTypes = query().orderByAsc("sort").list();

        // 4. 如果查询不到数据，返回提示错误信息
        if (CollectionUtil.isEmpty(shopTypes)){
            return Result.fail("商铺类型不存在...");
        }

        // 5. 将查询到的数据保存到redis中
        List<String> shopTypesJson = shopTypes.stream()
                .map(JSONUtil::toJsonStr)
                .collect(Collectors.toList());
        // 因为从数据库读出来的时候已经是按照顺序读出来的，这里想要维持顺序必须从右边push，类似队列
        stringRedisTemplate.opsForList().rightPushAll(typeListKey, shopTypesJson);
        // 6. 返回
        return Result.ok(shopTypes);
    }
}
