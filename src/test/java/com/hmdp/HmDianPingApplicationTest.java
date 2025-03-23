package com.hmdp;


import com.hmdp.entity.Shop;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @ClassName: HmDianPingApplicationTest
 * @Description:
 * @Author: csh
 * @Date: 2025-02-16 20:57
 */
@SpringBootTest
class HmDianPingApplicationTest {

    @Resource
    private ShopServiceImpl shopService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Test
    void test() {
        // 查询店铺信息
        List<Shop> shopList = shopService.list();
        // 根据店铺类型进行分组
        Map<Long, List<Shop>> shopGroup = shopList.stream().collect(Collectors.groupingBy(Shop::getTypeId));
        // 将店铺信息（id，坐标信息）写入redis
        for(Map.Entry<Long, List<Shop>> entry : shopGroup.entrySet()){
            Long typeId = entry.getKey();
            String key = RedisConstants.SHOP_GEO_KEY + typeId;
            List<Shop> shops = entry.getValue();
            List<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>(shops.size());
            for(Shop shop : shops){
                locations.add(new RedisGeoCommands.GeoLocation<>(shop.getId().toString(),new Point(shop.getX(),shop.getY())));
            }
            stringRedisTemplate.opsForGeo().add(key,locations);
        }
    }

}