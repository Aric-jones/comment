package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.extension.toolkit.Db;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollRequest;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;
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
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {
    @Resource
    private IUserService userService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IFollowService followService;

    /**
     * @param id
     * @return com.hmdp.dto.Result
     * @Author:CSH
     * @Updator:CSH
     * @Date 2025/3/19 20:23
     * @Description:
     */
    @Override
    public Result queryBlogById(Long id) {
        Blog blog = this.getById(id);
        if(Objects.isNull(blog)){
            return Result.fail("博客不存在");
        }
        queryBlogUser(blog);
        this.isBlogLiked(blog);
        return Result.ok(blog);
    }

    /**
     * @param blog
     * @return void
     * @Author:CSH
     * @Updator:CSH
     * @Date 2025/3/19 21:05
     * @Description:
     */
    private void isBlogLiked(Blog blog) {
        if(Objects.isNull(UserHolder.getUser())){
            return ;
        }
        // 获取当前登录用户
        Long userId = UserHolder.getUser().getId();
        // 判断是否点过赞
        String key = "blog:liked:" + blog.getId();
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        blog.setIsLike(Objects.nonNull(score));
    }

    /**
     * @param current
     * @return java.util.List<com.hmdp.entity.Blog>
     * @Author:CSH
     * @Updator:CSH
     * @Date 2025/3/19 20:27
     * @Description:
     */
    @Override
    public List<Blog> queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = this.query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog -> {
            this.queryBlogUser(blog);
            this.isBlogLiked(blog);
        });
        return records;
    }

    /**
     * @param blogId
     * @return void
     * @Author:CSH
     * @Updator:CSH
     * @Date 2025/3/19 20:54
     * @Description:
     */
    @Override
    public void likeBlog(Long blogId) {
        // 获取当前登录用户
        Long userId = UserHolder.getUser().getId();
        // 判断是否点过赞
        String key = RedisConstants.BLOG_LIKED_KEY + blogId;
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        Blog blog = this.getById(blogId);
        if(Objects.nonNull(score)){
            // 取消点赞
            blog.setLiked(blog.getLiked()-1);
            boolean isSuccess = this.update(blog, new LambdaQueryWrapper<Blog>().eq(Blog::getId, blogId));
            if(isSuccess){
                stringRedisTemplate.opsForZSet().remove(key,userId.toString());
            }

        }else{
            // 点赞
            blog.setLiked(blog.getLiked()+1);
            boolean isSuccess = this.update(blog, new LambdaQueryWrapper<Blog>().eq(Blog::getId, blogId));
            if(isSuccess){
                stringRedisTemplate.opsForZSet().add(key,userId.toString(),System.currentTimeMillis());
            }

        }
    }

    /**
     * @param blogId
     * @return com.hmdp.dto.Result
     * @Author:CSH
     * @Updator:CSH
     * @Date 2025/3/19 21:37
     * @Description:
     */
    @Override
    public Result queryBlogLikes(Long blogId) {
        String key = RedisConstants.BLOG_LIKED_KEY + blogId;
        Set<String> Top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        if(Objects.isNull(Top5)){
            return Result.ok(Collections.emptyList());
        }
        List<Long> ids = Top5.stream().map(Long::valueOf).collect(Collectors.toList());
        if(ids.isEmpty()){
            return Result.ok(Collections.emptyList());
        }
        String join = StrUtil.join(",", ids);
        List<UserDTO> users = userService.query().in("id", ids).last("order by field(id,"+join+")").list().stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(users);
    }

    /**
     * @param blog
     * @return java.lang.Long
     * @Author:CSH
     * @Updator:CSH
     * @Date 2025/3/20 15:53
     * @Description:
     */
    @Override
    public Result saveBlog(Blog blog) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 保存探店博文
        boolean isSuccess = this.save(blog);
        // 如果保存成功了，向粉丝推送消息
        if(!isSuccess){
            return Result.fail("新增笔记失败");
        }
        // 查询出粉丝
        Long userId = UserHolder.getUser().getId();
        List<Follow> list = Db.lambdaQuery(Follow.class).eq(Follow::getFollowUserId, userId).list();
        for(Follow follow : list){
            Long fanId = follow.getUserId();
            String key = RedisConstants.FEED_KEY + fanId;
            stringRedisTemplate.opsForZSet().add(key,blog.getId().toString(),System.currentTimeMillis());
        }
        return Result.ok(blog.getId());
    }

    /**
     * @param max
     * @param offset
     * @return com.hmdp.dto.Result
     * @Author:CSH
     * @Updator:CSH
     * @Date 2025/3/21 9:18
     * @Description:
     */
    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        // 获取用户信息
        Long userId = UserHolder.getUser().getId();
        String key = RedisConstants.FEED_KEY + userId;
        // 查询用户收件箱信息
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet().reverseRangeByScoreWithScores(key, 0, max, offset, 2);
        if(Objects.isNull(typedTuples)||typedTuples.isEmpty()){
            return Result.ok();
        }
        // 信息处理
        List<String> blogIds = new ArrayList<>(typedTuples.size());
        long minTime = 0L;
        int os = 1;

        for(ZSetOperations.TypedTuple<String> tuple : typedTuples){
            blogIds.add(tuple.getValue());
            long time = Objects.requireNonNull(tuple.getScore()).longValue();
            if(minTime == time){
                os++;
            }else{
                minTime = time;
                os = 1;
            }
        }
        List<Blog> blogs = this.query().in("id", blogIds).last("order by field(id," + StrUtil.join(",", blogIds) + ")").list();
        blogs.forEach(blog -> {
            this.queryBlogUser(blog);
            this.isBlogLiked(blog);
        });
        return Result.ok(ScrollRequest.builder().list(blogs).minTime(minTime).offset(os).build());
    }

    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());

    }
}
