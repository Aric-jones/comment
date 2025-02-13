package com.hmdp.interceptor;

import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.enums.BizCodeEnum;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.Objects;

/**
 * @ClassName: LoginInterceptor
 * @Description: 登录拦截器
 * @Author: csh
 * @Date: 2025-02-13 13:56
 */
public class LoginInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1. 获取session，判断用户是否存在
        HttpSession session = request.getSession();

        // 2. 不存在返回未登录提示
        Object user = session.getAttribute(SystemConstants.SESSION_USER);
        if(Objects.isNull(user)){
            response.setStatus(Integer.parseInt(BizCodeEnum.UN_LOGIN.getCode()));
        }

        // 3. 存在获取session中的用户信息，存放在ThreadLocal中
        UserHolder.saveUser((UserDTO) user);

        // 4. 放行
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 移除用户信息
        UserHolder.removeUser();
    }
}
