package hu.dmsone.archi.projector;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;

/** Worker: a tenant MongoDB klienseit a platform-reporting kezeli, a Spring Boot alapértelmezett kliense nem kell. */
@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class, MongoAutoConfiguration.class,
        MongoDataAutoConfiguration.class})
public class ProjectorApplication {
    public static void main(String[] args) {
        SpringApplication.run(ProjectorApplication.class, args);
    }
}
