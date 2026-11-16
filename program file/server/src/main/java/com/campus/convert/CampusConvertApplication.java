package com.campus.convert;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.campus.convert.mapper")
@EnableAsync
@EnableScheduling
public class CampusConvertApplication {

    public static void main(String[] args) {
        SpringApplication.run(CampusConvertApplication.class, args);
    }
}
