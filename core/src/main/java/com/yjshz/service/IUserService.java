package com.yjshz.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yjshz.dto.LoginFormDTO;
import com.yjshz.dto.Result;
import com.yjshz.entity.User;

import javax.servlet.http.HttpSession;


public interface IUserService extends IService<User> {

    Result sendCode(String phone, HttpSession session) ;

    Result login(LoginFormDTO loginForm, HttpSession session);

    void logout(String token);


}
