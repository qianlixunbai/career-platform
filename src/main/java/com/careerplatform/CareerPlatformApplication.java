package com.careerplatform;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan(basePackages = {
        "com.careerplatform.user.mapper",
        "com.careerplatform.profile.mapper",
        "com.careerplatform.career.mapper",
        "com.careerplatform.learning.mapper",
        "com.careerplatform.resume.mapper",
        "com.careerplatform.application.mapper"
})
public class CareerPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(CareerPlatformApplication.class, args);
    }

}
