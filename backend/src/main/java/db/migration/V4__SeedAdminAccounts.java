package db.migration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Seeds exactly two admin accounts. Credentials are read from
 * ADMIN1_EMAIL/ADMIN1_PASSWORD and ADMIN2_EMAIL/ADMIN2_PASSWORD env vars at
 * migration time so real deployments never need plaintext passwords checked
 * into SQL. The fallbacks below are placeholders for local dev only — see
 * README.md for why they must be overridden before any real deployment.
 */
public class V4__SeedAdminAccounts extends BaseJavaMigration {

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        insertAdmin(connection,
                envOrDefault("ADMIN1_EMAIL", "admin1@example.com"),
                envOrDefault("ADMIN1_PASSWORD", "ChangeMe123!"));
        insertAdmin(connection,
                envOrDefault("ADMIN2_EMAIL", "admin2@example.com"),
                envOrDefault("ADMIN2_PASSWORD", "ChangeMe456!"));
    }

    private void insertAdmin(Connection connection, String email, String password) throws Exception {
        String sql = "INSERT INTO admins (id, email, password_hash, created_at) VALUES (?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, UUID.randomUUID());
            statement.setString(2, email);
            statement.setString(3, ENCODER.encode(password));
            statement.setObject(4, Timestamp.from(Instant.now()));
            statement.executeUpdate();
        }
    }

    private static String envOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return (value == null || value.isBlank()) ? defaultValue : value;
    }
}
