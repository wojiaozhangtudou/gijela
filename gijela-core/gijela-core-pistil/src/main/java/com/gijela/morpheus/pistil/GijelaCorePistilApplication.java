package com.gijela.morpheus.pistil;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan(basePackages = "com.gijela.morpheus.pistil.mapper")
@SpringBootApplication
public class GijelaCorePistilApplication {
    public static void main(String[] args)
    {
        SpringApplication.run(GijelaCorePistilApplication.class,args);
    }
}
