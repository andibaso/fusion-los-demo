package sg.andi.fusionlos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * One Spring Boot app, two modes picked by profile:
 * "api" serves REST, "worker" runs the Temporal worker.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class FusionLosApplication {
    public static void main(String[] args) {
        SpringApplication.run(FusionLosApplication.class, args);
    }
}
