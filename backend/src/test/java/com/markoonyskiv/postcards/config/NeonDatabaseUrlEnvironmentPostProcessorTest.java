package com.markoonyskiv.postcards.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

class NeonDatabaseUrlEnvironmentPostProcessorTest {

    private final NeonDatabaseUrlEnvironmentPostProcessor processor = new NeonDatabaseUrlEnvironmentPostProcessor();

    private StandardEnvironment environmentWithDbUrl(String dbUrl) {
        StandardEnvironment environment = new StandardEnvironment();
        if (dbUrl != null) {
            environment.getPropertySources()
                    .addFirst(new MapPropertySource("test", java.util.Map.of("DB_URL", dbUrl)));
        }
        return environment;
    }

    @Test
    void rewritesNeonStyleUrlIntoJdbcUrlAndSeparateCredentials() {
        StandardEnvironment environment =
                environmentWithDbUrl("postgresql://myuser:mypass@ep-abc-123.us-east-2.aws.neon.tech/postcards?sslmode=require");

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("spring.datasource.url"))
                .isEqualTo("jdbc:postgresql://ep-abc-123.us-east-2.aws.neon.tech:5432/postcards?sslmode=require");
        assertThat(environment.getProperty("spring.datasource.username")).isEqualTo("myuser");
        assertThat(environment.getProperty("spring.datasource.password")).isEqualTo("mypass");
    }

    @Test
    void preservesExplicitPortWhenPresent() {
        StandardEnvironment environment =
                environmentWithDbUrl("postgres://myuser:mypass@ep-abc-123.aws.neon.tech:6543/postcards?sslmode=require");

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("spring.datasource.url"))
                .isEqualTo("jdbc:postgresql://ep-abc-123.aws.neon.tech:6543/postcards?sslmode=require");
    }

    @Test
    void leavesExistingJdbcUrlUntouched() {
        StandardEnvironment environment = environmentWithDbUrl("jdbc:postgresql://localhost:5432/postcards");

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("spring.datasource.url")).isNull();
        assertThat(environment.getProperty("spring.datasource.username")).isNull();
    }

    @Test
    void isNoOpWhenDbUrlUnset() {
        StandardEnvironment environment = environmentWithDbUrl(null);

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("spring.datasource.url")).isNull();
    }
}
