package com.hmdp;


import com.hmdp.service.impl.ShopServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;

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

    @Test
    void test() throws InterruptedException {
        shopService.saveShop2Redis(1L,10L);
    }

}