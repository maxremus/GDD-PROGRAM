package org.example.gp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class GpApplication {

    public static void main(String[] args) {
        SpringApplication.run(GpApplication.class, args);
    }

}
