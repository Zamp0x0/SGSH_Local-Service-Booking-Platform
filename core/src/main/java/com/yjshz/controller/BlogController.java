package com.yjshz.controller;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yjshz.dto.Result;
import com.yjshz.dto.UserDTO;
import com.yjshz.entity.Blog;
import com.yjshz.entity.User;
import com.yjshz.service.IBlogService;
import com.yjshz.service.IUserService;
import com.yjshz.utils.SystemConstants;
import com.yjshz.utils.UserHolder;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;


@RestController
@RequestMapping("/blog")
public class BlogController {

    @Resource
    private IBlogService blogService;
    @PostMapping
    public Result saveBlog(@RequestBody Blog blog) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 保存探店博文
        blogService.saveBlog(blog);
        // 返回id
        return Result.ok(blog.getId());
    }

    @PutMapping("/like/{id}")
    public Result likeBlog(@PathVariable("id") Long id) {
        // 类似一人一单问题

        return blogService.likeBlog(id);
    }

    @GetMapping("/of/me")
    public Result queryMyBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        // 根据用户查询
        Page<Blog> page = blogService.query()
                .eq("user_id", user.getId()).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        return Result.ok(records);
    }

    @GetMapping("/of/follow")
    public Result queryBlogofFollow(@RequestParam("lastId")Long max,
                                    @RequestParam(value = "offset",defaultValue = "0")Integer offset){
        // 上一次查询的 最小时间戳  即本次查询的最大时间戳
        // 第一次来查询的偏移量为 0
        return blogService.queryBlogOfFollow(max,offset);
    }

    @GetMapping("/hot")
    public Result queryHotBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        return blogService.queryHotBlog(current);
    }

    @GetMapping("/{id}")
    public Result queryBlogById(@PathVariable("id")Long id){
        return blogService.queryBlogById(id);
    }

    @GetMapping("/likes/{id}")
    public Result queryBlogLikes(@PathVariable("id")Long id){return blogService.queryBlogLikes(id);}
}
