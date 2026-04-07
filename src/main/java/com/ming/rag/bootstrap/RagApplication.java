package com.ming.rag.bootstrap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication(scanBasePackages = "com.ming.rag")
@ConfigurationPropertiesScan(basePackages = "com.ming.rag.bootstrap.config")
public class RagApplication {

    public static void main(String[] args) {
        SpringApplication.run(RagApplication.class, args);
    }
}
