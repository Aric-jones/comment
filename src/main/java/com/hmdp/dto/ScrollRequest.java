package com.hmdp.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * @ClassName: ScrollRequest
 * @Description: 分页
 * @Author: CSH
 * @Date: 2025-03-21 09:13
 */
@Data
@Builder
public class ScrollRequest {
    private List<?> list;
    private Long minTime;
    private Integer offset;
}
