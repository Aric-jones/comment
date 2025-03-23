package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IBlogService extends IService<Blog> {

    /**
     * @param id
     * @return com.hmdp.dto.Result
     * @Author:CSH
     * @Updator:CSH
     * @Date 2025/3/19 20:23
     * @Description:
     */
    Result queryBlogById(Long id);

    /**
     * @param current
     * @return java.util.List<com.hmdp.entity.Blog>
     * @Author:CSH
     * @Updator:CSH
     * @Date 2025/3/19 20:27
     * @Description:
     */
    List<Blog> queryHotBlog(Integer current);

    /**
     * @param id
     * @return void
     * @Author:CSH
     * @Updator:CSH
     * @Date 2025/3/19 20:54
     * @Description:
     */
    void likeBlog(Long id);

    /**
     * @param blogId
     * @return com.hmdp.dto.Result
     * @Author:CSH
     * @Updator:CSH
     * @Date 2025/3/19 21:37
     * @Description:
     */
    Result queryBlogLikes(Long blogId);

    /**
     * @param blog
     * @return com.hmdp.dto.Result
     * @Author:CSH
     * @Updator:CSH
     * @Date 2025/3/20 15:53
     * @Description:
     */
    Result saveBlog(Blog blog);

    /**
     * @param max
     * @param offset
     * @return com.hmdp.dto.Result
     * @Author:CSH
     * @Updator:CSH
     * @Date 2025/3/21 9:18
     * @Description:
     */
    Result queryBlogOfFollow(Long max, Integer offset);
}
