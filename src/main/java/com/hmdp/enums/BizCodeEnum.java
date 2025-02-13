package com.hmdp.enums;

import lombok.Getter;

/**
 * @ClassName: BizCodeEnum
 * @Description: 异常枚举
 * @Author: csh
 * @Date: 2025-02-13 14:04
 */
@Getter
public enum BizCodeEnum {
    /**
     * 未登录
     */
    UN_LOGIN("401", "未登录");

    private final String code;

    private final String desc;


    BizCodeEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
