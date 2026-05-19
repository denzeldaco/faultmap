package io.faultmap.faultmap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication(scanBasePackages = "io.faultmap")
@ConfigurationPropertiesScan("io.faultmap.config")
@EnableJpaRepositories(basePackages = "io.faultmap")
@EntityScan(basePackages = "io.faultmap")
@EnableAsync
public class FaultmapApplication {

    public static void main(String[] args) {
        SpringApplication.run(FaultmapApplication.class, args);
    }

}
