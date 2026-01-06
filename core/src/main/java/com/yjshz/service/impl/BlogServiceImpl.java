package com.yjshz.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yjshz.dto.Result;
import com.yjshz.dto.ScrollResult;
import com.yjshz.dto.UserDTO;
import com.yjshz.entity.Blog;
import com.yjshz.entity.Follow;
import com.yjshz.entity.User;
import com.yjshz.mapper.BlogMapper;
import com.yjshz.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yjshz.service.IFollowService;
import com.yjshz.service.IUserService;
import com.yjshz.utils.RedisConstants;
import com.yjshz.utils.SystemConstants;
import com.yjshz.utils.UserHolder;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;


@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private IUserService userService;

    @Resource
    private IFollowService followService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private static final DefaultRedisScript<Long> LIKE_SCRIPT;

    static{
        LIKE_SCRIPT = new DefaultRedisScript<>();
        LIKE_SCRIPT.setLocation(new ClassPathResource("./like.lua"));
        LIKE_SCRIPT.setResultType(Long.class);
    }

    /**
     * 查询博客的发布者信息
     * @param blog
     */
    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }

    private void isBlogLiked(Blog blog){
        UserDTO user = UserHolder.getUser();
        if(user == null){
            return;
        }
        Long userId = user.getId();
        String key = "blog:liked:" + blog.getId();
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        blog.setIsLike(score != null);
    }
    @Override
    public Result queryBlogById(Long id) {
        Blog blog = getById(id);
        if (blog == null) {
            return Result.fail("图文不存在");
        }
        queryBlogUser(blog);
        isBlogLiked(blog);
        return Result.ok(blog);
    }


    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog -> {
            this.queryBlogUser(blog);
            this.isBlogLiked(blog);
        });
        return Result.ok(records);
    }

    @Override
    public Result likeBlog(Long id) {
        //获取登录用户,入参id是Blog的id
        Long userId = UserHolder.getUser().getId();
        //String key = "blog:liked" + id;
        String key = "blog:liked:" + id;
        String countKey = "blog:liked:count:" + id;
        /*
        List<String> keys = Arrays.asList(
                "blog:liked:" + id,
                "blog:liked:count:" + id
        );

        stringRedisTemplate.execute(LIKE_SCRIPT, keys, userId.toString());

        CompletableFuture.runAsync(() -> {
            Long count = stringRedisTemplate.opsForSet().size(key);
            update().set("liked", count).eq("id", id).update();
        });
        */
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        if(score == null){        //未点赞 点赞数+1、保存用户到set
            boolean isSuccess = update().setSql("liked = liked + 1").eq("id", id).update();
            if (isSuccess) {
                stringRedisTemplate.opsForZSet().add(key,userId.toString(),System.currentTimeMillis());
            }

        }else{ //已点赞 点赞数-1 从set移除
            boolean isSuccess = update().setSql("liked = liked - 1").eq("id", id).update();
            if (isSuccess) {
                stringRedisTemplate.opsForZSet().remove(key,userId.toString());
            }
        }

        return Result.ok();
    }

    @Override
    public Result queryBlogLikes(Long id) {
        // 1) 从 Redis 按“最新点赞”取前 4 个（0~3）
        String key = RedisConstants.BLOG_LIKED_KEY + id;
        Set<String> top = stringRedisTemplate.opsForZSet().reverseRange(key, 0, 3);
        if (top == null || top.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }

        // 2) 保持顺序：Set -> List（reverseRange 返回的顺序就是你要的顺序）
        List<Long> ids = top.stream().map(Long::valueOf).collect(Collectors.toList());

        // 3) 用 ORDER BY FIELD 保证数据库查询结果顺序与 ids 一致
        String idStr = StrUtil.join(",", ids);
        List<UserDTO> userDTOS = userService.query()
                .in("id", ids)
                .last("ORDER BY FIELD(id," + idStr + ")")
                .list()
                .stream()
                .map(u -> BeanUtil.copyProperties(u, UserDTO.class))
                .collect(Collectors.toList());

        return Result.ok(userDTOS);
    }


    /**
     * 发布笔记后推送到粉丝的信箱中
     * @param blog
     * @return blogId
     */
    @Override
    public Result saveBlog(Blog blog){
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        boolean isSuccess = save(blog);
        if(!isSuccess){
            return Result.fail("新增笔记失败");
        }
        List<Follow> follows = followService.query().eq("follow_user_id", user.getId()).list();
        List<Long> followUserIds = follows.stream()
                .map(Follow::getFollowUserId)
                .collect(Collectors.toList());

        for(Long followIds:followUserIds){
            String key = RedisConstants.FEED_KEY + followIds;
            stringRedisTemplate.opsForZSet().add(key,blog.getId().toString(),System.currentTimeMillis());

        }
        return Result.ok(blog.getId());
    }

    /**
     * 查询关注列表推送的博客：收集信箱中的笔记，做滚动分页
     * @param max
     * @param offset
     * @return
     */
    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        String key = RedisConstants.FEED_KEY + UserHolder.getUser().getId();
        //zrevRangeByScore key max min limit offset count
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(key, 0, max, offset, 2);
        if(typedTuples == null || typedTuples.isEmpty()){
            return Result.ok();
        }

        // 3、解析数据：blogId、 score(minTime)时间戳  offset:本次查询的最小值一样的元素个数,  作为下次查询的参数传递进啦
        List<Long> blogIds = new ArrayList<>(typedTuples.size());
        long minTime = 0;
        int os = 1;
        // typedTuples 本身是降序的  最后一个的score即时间戳一定是最小的
        for (ZSetOperations.TypedTuple<String> tuple : typedTuples) {
            // 3.1获取笔记ID
            blogIds.add(Long.valueOf(tuple.getValue()));
            // 3.2获取分数(时间戳)
            long scoreTime = tuple.getScore().longValue();
            // 3.3判断偏移量：本次查询最小的score时间戳 相同的有几个
            if (minTime == scoreTime) {
                os++;
            } else {
                minTime = scoreTime;
                os = 1;
            }
        }
        // 4、根据id查询blog
        String blogIdStr = StrUtil.join(",", blogIds);//把blogIds拼接成字符串
        List<Blog> blogs = query().in("id", blogIds).last("ORDER BY FIELD(id," + blogIdStr + ")").list();
        // 这里由于 查看笔记时 可以看到整个笔记对应发布者的用户信息  所以Blog实体类里面有用户的 icon、name
        // 并且由于笔记也可以查看自己有没有点赞,所以blog实体类的isliked属性也需要赋值
        for(Blog blog:blogs){
            queryBlogUser(blog);
            isBlogLiked(blog);
        }
        // 5、封装并返回
        ScrollResult scrollResult = new ScrollResult();
        scrollResult.setList(blogs);        //笔记集合
        scrollResult.setOffset(os);         //下一次查询所要传递的偏移量参数
        scrollResult.setMinTime(minTime);   //本次查询的最小时间戳，下次查询的参数(最大时间戳)

        return Result.ok(scrollResult);
    }

}
