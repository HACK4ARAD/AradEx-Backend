package aradexperience;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Aplicația Spring Boot. Stă în pachetul rădăcină `aradexperience`, deci scanarea implicită acoperă toate layerele
 * (controller, service, repository, entity, mapper, config, security, error, util) fără configurări suplimentare.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
