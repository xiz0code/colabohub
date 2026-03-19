package com.colaborapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ColaborAppApplication {

    public static void main(String[] args) {
        SpringApplication.run(ColaborAppApplication.class, args);
    }
}
