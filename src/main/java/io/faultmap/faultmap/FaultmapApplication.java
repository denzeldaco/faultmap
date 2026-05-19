package io.faultmap.faultmap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan("io.faultmap.config")
public class FaultmapApplication {

    public static void main(String[] args) {
        SpringApplication.run(FaultmapApplication.class, args);
    }

}
