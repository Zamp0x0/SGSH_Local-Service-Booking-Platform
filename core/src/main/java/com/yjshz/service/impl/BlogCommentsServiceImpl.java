package com.yjshz.service.impl;

import com.yjshz.entity.BlogComments;
import com.yjshz.mapper.BlogCommentsMapper;
import com.yjshz.service.IBlogCommentsService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;


@Service
public class BlogCommentsServiceImpl extends ServiceImpl<BlogCommentsMapper, BlogComments> implements IBlogCommentsService {

}
