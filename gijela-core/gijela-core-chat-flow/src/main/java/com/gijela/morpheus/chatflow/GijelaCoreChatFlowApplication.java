package com.gijela.morpheus.chatflow;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("com.gijela.morpheus.chatflow.mapper")
@SpringBootApplication
public class GijelaCoreChatFlowApplication {

    public static void main(String[] args) {
        SpringApplication.run(GijelaCoreChatFlowApplication.class, args);
    }
}
