package com.yjshz;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("com.yjshz.mapper")
@SpringBootApplication
public class YJSHZApplication {

    public static void main(String[] args) {
        SpringApplication.run(YJSHZApplication.class, args);
    }

}
