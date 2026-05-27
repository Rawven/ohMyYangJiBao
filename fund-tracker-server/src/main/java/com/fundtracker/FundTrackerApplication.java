package com.fundtracker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FundTrackerApplication {
    public static void main(String[] args) {
        SpringApplication.run(FundTrackerApplication.class, args);
    }
}
