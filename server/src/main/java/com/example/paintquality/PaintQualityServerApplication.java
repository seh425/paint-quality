package com.example.paintquality;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;
import java.time.ZoneId;

@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
public class PaintQualityServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaintQualityServerApplication.class, args);
    }

    @Bean
    Clock clock(AppProperties properties) {
        return Clock.system(ZoneId.of(properties.getZoneId()));
    }
}
