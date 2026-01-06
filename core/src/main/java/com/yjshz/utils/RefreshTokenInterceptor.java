package com.yjshz.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.yjshz.dto.UserDTO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class RefreshTokenInterceptor implements HandlerInterceptor {

    private final StringRedisTemplate stringRedisTemplate;

    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 1) 取 token
        String token = request.getHeader("authorization");
        if (StrUtil.isBlank(token)) {
            return true; // 没 token 直接放行，交给 LoginInterceptor 判断要不要拦
        }

        // 2) 查 Redis
        String key = RedisConstants.LOGIN_USER_KEY + token;
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(key);
        if (userMap == null || userMap.isEmpty()) {
            return true; // token 无效/过期，也放行，交给 LoginInterceptor
        }

        // 3) 转成 UserDTO
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);

        // 4) 存入 ThreadLocal（关键：把 token 也存进去，登出要用）
        UserHolder.saveUser(userDTO);
        UserHolder.saveToken(token);

        // 5) 刷新 token 过期时间（滑动过期）
        stringRedisTemplate.expire(key, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserHolder.removeUser(); // 会同时清理 user + token（你已经改了 UserHolder 才行）
    }
}
