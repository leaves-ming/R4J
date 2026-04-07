package com.ming.rag.bootstrap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication(
        scanBasePackages = "com.ming.rag",
        exclude = {
                DataSourceAutoConfiguration.class,
                FlywayAutoConfiguration.class
        }
)
@ConfigurationPropertiesScan(basePackages = "com.ming.rag.bootstrap.config")
public class RagApplication {

    public static void main(String[] args) {
        SpringApplication.run(RagApplication.class, args);
    }
}
