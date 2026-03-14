package com.example.dianzan;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.example.dianzan.mapper")   // 关键：扫描 Mapper 接口
public class DianzanApplication {
    public static void main(String[] args) {
        SpringApplication.run(DianzanApplication.class, args);
    }
}