package com.limiteddrop;

import com.limiteddrop.config.ReservationProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableScheduling
@EnableCaching
@EnableConfigurationProperties(ReservationProperties.class)
public class LimitedDropApplication {
    public static void main(String[] args) {
        SpringApplication.run(LimitedDropApplication.class, args);
    }
}
