package com.ai2qa;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
@EnableAsync
public class Ai2QaApplication {

    private static final Logger log = LoggerFactory.getLogger(Ai2QaApplication.class);

    public static void main(String[] args) {
        log.info("Starting Ai2QA - The Automated QA Hunter");
        SpringApplication.run(Ai2QaApplication.class, args);
        log.info("Ai2QA started successfully");
    }
}
