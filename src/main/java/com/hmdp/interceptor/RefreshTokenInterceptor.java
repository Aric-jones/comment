package com.hmdp.interceptor;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.hmdp.dto.UserDTO;
import com.hmdp.enums.BizCodeEnum;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * @ClassName: ReflashTokenInterceptor
 * @Description: Token刷新拦截器
 * @Author: csh
 * @Date: 2025-02-13 16:21
 */
public class RefreshTokenInterceptor implements HandlerInterceptor {
    private StringRedisTemplate stringRedisTemplate;

    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 获取token
        String token = request.getHeader(SystemConstants.TOKEN);

        // 判断token是否存在
        if(StringUtils.isBlank(token)){
            return true;
        }

        // 基于token获取redis中的用户数据
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(RedisConstants.LOGIN_USER_KEY + token);

        // 判断用户是否存在
        if(userMap.isEmpty()){
            return true;
        }

        // 将userMap装换成UserDTO
        UserDTO user = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);

        // 将用户信息存放在ThreadLocal中
        UserHolder.saveUser(user);

        // 刷新token有效期
        stringRedisTemplate.expire(RedisConstants.LOGIN_USER_KEY + token, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);

        // 放行
        return true;
    }

}
