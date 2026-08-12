package info.gamed.glm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.scheduling.annotation.EnableScheduling;

import info.gamed.glm.config.GameProperties;

/**
 * Main class of the Spring Boot application.
 *
 * Extends {@link SpringBootServletInitializer} so the same artifact works two ways: it runs with the
 * embedded server (via 'mvn spring-boot:run' or 'java -jar') for development, and it can be deployed as a
 * WAR to an external servlet container (Tomcat) for production - the container calls {@link #configure}
 * instead of {@link #main}.
 * @author Z@
 */
@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(GameProperties.class)
public class GlmApplication extends SpringBootServletInitializer {

    private static final Logger log = LoggerFactory.getLogger(GlmApplication.class);

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
        return application.sources(GlmApplication.class);
    }

    public static void main(String[] args) {
        log.info("GLM application started");
        SpringApplication.run(GlmApplication.class, args);
    }
}
