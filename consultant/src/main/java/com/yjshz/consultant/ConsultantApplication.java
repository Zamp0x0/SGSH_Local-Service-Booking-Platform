package com.yjshz.consultant;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication(scanBasePackages = "com.yjshz.consultant")
@MapperScan("com.yjshz.consultant.mapper")
public class ConsultantApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConsultantApplication.class, args);
    }

}
