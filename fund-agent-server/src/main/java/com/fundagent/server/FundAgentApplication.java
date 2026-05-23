package com.fundagent.server;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.fundagent")
@MapperScan("com.fundagent.repo.mapper")
public class FundAgentApplication {
    public static void main(String[] args) {
        SpringApplication.run(FundAgentApplication.class, args);
    }
}
