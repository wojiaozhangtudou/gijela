package com.gijela.morpheus.chat;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@MapperScan(basePackages = {
    "com.gijela.morpheus.chat.mapper",
    "com.gijela.morpheus.chat.llm.log.alert.mapper"
})
@ConfigurationPropertiesScan(basePackages = "com.gijela.morpheus.chat.config")
@SpringBootApplication
public class GijelaCoreChatApplication {

    public static void main(String[] args) {
        SpringApplication.run(GijelaCoreChatApplication.class, args);
    }
}
