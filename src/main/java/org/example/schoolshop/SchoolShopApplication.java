package org.example.schoolshop;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("org.example.schoolshop.mapper")
@EnableScheduling
public class SchoolShopApplication {

    public static void main(String[] args) {
        SpringApplication.run(SchoolShopApplication.class, args);
    }
}
