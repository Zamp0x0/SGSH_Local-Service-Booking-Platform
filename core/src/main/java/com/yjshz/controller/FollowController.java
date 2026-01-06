package com.yjshz.controller;


import com.yjshz.dto.Result;
import com.yjshz.service.IFollowService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("/follow")
public class FollowController {

    @Resource
    private IFollowService followService;

    /**
     * 关注用户
     * @param followUserId 关注用户的id
     * @param isFollow 是否已关注
     * @return
     */
    @PutMapping("/{id}/{isFollow}")
    public Result follow(@PathVariable("id") Long followUserId, @PathVariable("isFollow") Boolean isFollow){
        return followService.follow(followUserId, isFollow);
    }

    /**
     * 是否关注用户
     * @param followUserId 关注用户的id
     * @return
     */
    @GetMapping("/or/not/{id}")
    public Result isFollow(@PathVariable("id") Long followUserId){
        return followService.isFollow(followUserId);
    }

    @GetMapping("/follow/common/{id}")
    public Result followCommons(@PathVariable("id")Long id){
        return followService.followCommons(id);
    }
}
