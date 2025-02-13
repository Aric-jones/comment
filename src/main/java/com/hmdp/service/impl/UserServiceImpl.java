package com.hmdp.service.impl;


import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;
import java.util.Objects;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Override
    public Result sendCode(String phone, HttpSession session) {
        // 1. 校验手机号格式
        if(RegexUtils.isPhoneInvalid(phone)){
            // 2. 格式不正确返回错误信息
            return Result.fail("手机号格式不正确");
        }

        // 3. 生成验证码
        String code = RandomUtil.randomNumbers(6);

        // 4. 保存验证码到session中
        session.setAttribute("code",code);

        // 5. 发送短信验证码
        log.info("发送短信验证码成功，验证码为：{}",code);
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        // 1. 验证手机号
        String phone = loginForm.getPhone();
        if(RegexUtils.isPhoneInvalid(phone)){
            return Result.fail(SystemConstants.PHONE_FORMAT_ERROR);
        }

        // 2. 验证验证码
        if(Objects.nonNull(loginForm.getCode()) && !loginForm.getCode().equals(session.getAttribute(SystemConstants.SESSION_CODE))){
            return Result.fail(SystemConstants.SESSION_CODE_ERROR);
        }

        // 3. 查询用户是否存在
        User user = this.getOne(new LambdaQueryWrapper<User>().eq(User::getPhone, phone));
        if(Objects.isNull(user)){
            // 4. 不存在创建用户
            user = createUserWithPhone(phone);
        }


        // 5. 登录成功，将用户信息保存到session中
        session.setAttribute("user",user);

        return Result.ok();
    }

    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(SystemConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        this.save(user);
        return user;
    }
}
