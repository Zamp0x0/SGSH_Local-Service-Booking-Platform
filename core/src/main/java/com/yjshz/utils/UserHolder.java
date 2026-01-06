package com.yjshz.utils;

import com.yjshz.dto.UserDTO;

public class UserHolder {

    private static final ThreadLocal<UserDTO> tl = new ThreadLocal<>();
    private static final ThreadLocal<String> tokenTL = new ThreadLocal<>();

    public static void saveUser(UserDTO user){
        tl.set(user);
    }

    public static UserDTO getUser(){
        return tl.get();
    }

    public static void removeUser(){
        tl.remove();
        tokenTL.remove();
    }

    public static void saveToken(String token){
        tokenTL.set(token);
    }

    public static String getToken(){
        return tokenTL.get();
    }
}
