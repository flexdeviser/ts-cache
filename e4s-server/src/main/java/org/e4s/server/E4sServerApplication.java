package org.e4s.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class E4sServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(E4sServerApplication.class, args);
    }
}