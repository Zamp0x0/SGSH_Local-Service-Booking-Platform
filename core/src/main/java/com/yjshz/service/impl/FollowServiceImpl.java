package com.yjshz.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yjshz.dto.Result;
import com.yjshz.dto.UserDTO;
import com.yjshz.entity.Follow;
import com.yjshz.mapper.FollowMapper;
import com.yjshz.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yjshz.service.IUserService;
import com.yjshz.utils.RedisConstants;
import com.yjshz.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;


@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IUserService userService;
    /**
     * 关注用户
     *
     * @param followUserId 关注用户的id
     * @param isFollow     是否已关注
     * @return
     */
    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        Long userId = UserHolder.getUser().getId();
        String key = RedisConstants.USER_FOLLOW_KEY + userId;
        if( isFollow ){
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(followUserId);
            boolean isSuccess = save(follow);
            if (isSuccess) {
                stringRedisTemplate.opsForSet().add(key,followUserId.toString());
            }
        }else{
            //Follow::getUserId 通过反射得到字段名为user_id
            boolean isSuccess = remove(new LambdaQueryWrapper<Follow>()
                    .eq(Follow::getUserId, userId)
                    .eq(Follow::getFollowUserId, followUserId));
            if (isSuccess) {
                stringRedisTemplate.opsForSet().remove(key,followUserId.toString());
            }
        }
        return Result.ok();
    }

    /**
     * 是否关注用户
     *
     * @param followUserId 关注用户的id
     * @return
     */
    @Override
    public Result isFollow(Long followUserId) {
        Long userId = UserHolder.getUser().getId();
        int count = count(new LambdaQueryWrapper<Follow>()
                .eq(Follow::getUserId, userId)
                .eq(Follow::getFollowUserId, followUserId));
        return Result.ok(count > 0);
    }

    /**
     * 查找自己和别人的共同关注的用户
     *
     * @param id id
     * @return {@link Result}
     */
    @Override
    public Result followCommons(Long id){
        //当前用户
        Long userId = UserHolder.getUser().getId();
        String currentUserKey = RedisConstants.USER_FOLLOW_KEY + userId;
        String targetUserKey = RedisConstants.USER_FOLLOW_KEY + id;
        //求两个set集合
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(currentUserKey, targetUserKey);
        if(intersect == null || intersect.isEmpty()){
            return Result.ok(Collections.emptyList());
        }
        List<Long> commonIds = intersect.stream().map(Long::valueOf).collect(Collectors.toList());

        //利用共同关注账号的ID找到对应的用户信息
        List<UserDTO> collect = userService.listByIds(commonIds)
                .stream()
                .map((user) -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());

        return Result.ok(collect);

    }

}
