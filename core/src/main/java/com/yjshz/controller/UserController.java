package com.yjshz.controller;


import com.yjshz.dto.LoginFormDTO;
import com.yjshz.dto.Result;
import com.yjshz.dto.UserDTO;
import com.yjshz.entity.User;
import com.yjshz.entity.UserInfo;
import com.yjshz.service.IUserInfoService;
import com.yjshz.service.IUserService;
import com.yjshz.service.impl.UserServiceImpl;
import com.yjshz.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;


@Slf4j
@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private IUserService userService;

    @Resource
    private IUserInfoService userInfoService;

    /**
     * 发送手机验证码
     */
    @PostMapping("code")
    public Result sendCode(@RequestParam("phone") String phone, HttpSession session) {
        // TODO 发送短信验证码并保存验证码
        return userService.sendCode(phone,session);
    }

    /**
     * 登录功能
     * @param loginForm 登录参数，包含手机号、验证码；或者手机号、密码
     */
    @PostMapping("/login")
    public Result login(@RequestBody LoginFormDTO loginForm, HttpSession session){
        // TODO 实现登录功能

        return userService.login(loginForm,session);
    }

    /**
     * 登出功能
     * @return 无
     */
    @PostMapping("/logout")
    public Result logout(){
        // 1. 获取请求头中的 token
        String token = UserHolder.getToken();
        if (token == null) {
            return Result.ok();
        }

        // 2. 删除 Redis 中的登录信息
        userService.logout(token);

        return Result.ok();
    }


    @GetMapping("/me")
    public Result me(){
       UserDTO user = UserHolder.getUser();
        return Result.ok(user);
    }

    @GetMapping("/info/{id}")
    public Result info(@PathVariable("id") Long userId){
        // 查询详情
        UserInfo info = userInfoService.getById(userId);
        if (info == null) {
            // 没有详情，应该是第一次查看详情
            return Result.ok();
        }
        info.setCreateTime(null);
        info.setUpdateTime(null);
        // 返回
        return Result.ok(info);
    }
}
