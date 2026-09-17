package com.markoonyskiv.postcards.config;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Rewrites a libpq-style {@code DB_URL} - {@code postgres(ql)://user:password@host/db?sslmode=require},
 * the format Neon (and most managed Postgres providers) hand out - into the
 * separate {@code spring.datasource.url}/{@code username}/{@code password}
 * properties the PostgreSQL JDBC driver actually expects.
 *
 * <p>Pgjdbc's URL parser has no notion of userinfo ({@code user:password@})
 * in the authority section: a raw libpq URI passed straight through as
 * {@code spring.datasource.url} fails to connect, since the driver tries to
 * resolve {@code user:password@host} itself as the hostname. Query
 * parameters - crucially {@code sslmode} - are carried over untouched, so
 * Neon's required-SSL connections still enforce SSL as requested.
 *
 * <p>A no-op when {@code DB_URL} is unset or already a {@code jdbc:} URL (the
 * local Docker Postgres default in {@code application.yml}), so local dev is
 * unaffected.
 */
public class NeonDatabaseUrlEnvironmentPostProcessor implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String dbUrl = environment.getProperty("DB_URL");
        if (dbUrl == null || dbUrl.startsWith("jdbc:")) {
            return;
        }
        if (!dbUrl.startsWith("postgres://") && !dbUrl.startsWith("postgresql://")) {
            return;
        }

        URI uri;
        try {
            uri = new URI(dbUrl);
        } catch (URISyntaxException e) {
            // Leave it alone - Spring/the driver will surface a clear error at
            // startup rather than this post-processor failing silently wrong.
            return;
        }

        String userInfo = uri.getUserInfo();
        if (userInfo == null || !userInfo.contains(":")) {
            return;
        }
        int separator = userInfo.indexOf(':');
        String username = userInfo.substring(0, separator);
        String password = userInfo.substring(separator + 1);

        int port = uri.getPort() == -1 ? 5432 : uri.getPort();
        String database = uri.getPath() == null ? "" : uri.getPath();
        String query = uri.getQuery() == null ? "" : "?" + uri.getQuery();
        String jdbcUrl = "jdbc:postgresql://" + uri.getHost() + ":" + port + database + query;

        Map<String, Object> overrides = new LinkedHashMap<>();
        overrides.put("spring.datasource.url", jdbcUrl);
        overrides.put("spring.datasource.username", username);
        overrides.put("spring.datasource.password", password);

        environment.getPropertySources().addFirst(new MapPropertySource("neonDatabaseUrl", overrides));
    }
}
