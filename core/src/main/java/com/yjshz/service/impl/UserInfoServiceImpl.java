package com.yjshz.service.impl;

import com.yjshz.entity.UserInfo;
import com.yjshz.mapper.UserInfoMapper;
import com.yjshz.service.IUserInfoService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;


@Service
public class UserInfoServiceImpl extends ServiceImpl<UserInfoMapper, UserInfo> implements IUserInfoService {

}
