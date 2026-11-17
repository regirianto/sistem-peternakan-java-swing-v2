package id.peternakan.service.db;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

/**
 * Migrasi bernomor V{n}__{nama}.sql. Setiap file diterapkan sekali dan dicatat
 * pada schema_migrations; menjalankan ulang aman karena versi terpasang dilewati.
 */
public final class Migrations {

    public record Migration(int version, String name, String sql) {
    }

    public static void run(Db db) {
        try (Connection c = db.rawConnection()) {
            c.setAutoCommit(true);
            try (Statement st = c.createStatement()) {
                st.execute("""
                    CREATE TABLE IF NOT EXISTS schema_migrations (
                      version INT PRIMARY KEY,
                      name VARCHAR(200) NOT NULL,
                      checksum CHAR(64) NOT NULL,
                      applied_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
            }
            List<Migration> migrations = load();
            for (Migration m : migrations) {
                if (applied(c, m.version())) {
                    continue;
                }
                apply(c, m);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Migrasi gagal: " + e.getMessage(), e);
        }
    }

    private static boolean applied(Connection c, int version) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM schema_migrations WHERE version = ?")) {
            ps.setInt(1, version);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static void apply(Connection c, Migration m) throws SQLException {
        try {
            c.setAutoCommit(false);
            for (String statement : split(m.sql())) {
                if (statement.isBlank()) {
                    continue;
                }
                try (Statement st = c.createStatement()) {
                    st.execute(statement);
                }
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO schema_migrations (version, name, checksum) VALUES (?, ?, ?)")) {
                ps.setInt(1, m.version());
                ps.setString(2, m.name());
                ps.setString(3, sha256(m.sql()));
                ps.executeUpdate();
            }
            c.commit();
        } catch (SQLException e) {
            c.rollback();
            throw new SQLException("Migrasi V" + m.version() + " gagal: " + e.getMessage(), e);
        } finally {
            c.setAutoCommit(true);
        }
    }

    static List<String> split(String sql) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inString = false;
        boolean inComment = false;
        for (int i = 0; i < sql.length(); i++) {
            char ch = sql.charAt(i);
            if (inComment) {
                if (ch == '\n') {
                    inComment = false;
                    current.append(ch);
                }
                continue;
            }
            if (!inString && ch == '-' && i + 1 < sql.length() && sql.charAt(i + 1) == '-') {
                inComment = true;
                i++;
                continue;
            }
            if (ch == '\'') {
                inString = !inString;
                current.append(ch);
                continue;
            }
            if (ch == ';' && !inString) {
                result.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        if (!current.toString().isBlank()) {
            result.add(current.toString().trim());
        }
        return result;
    }

    private static List<Migration> load() {
        List<Migration> list = new ArrayList<>();
        String[] names = {
                "V1__schema.sql", "V2__seed.sql", "V3__meat.sql", "V4__remove_discount.sql",
                "V5__remove_cash.sql", "V6__rename_company.sql", "V7__branches.sql", "V8__remove_rph_type.sql", "V9__goat_jenis.sql", "V10__receipt_status.sql"
        };
        for (String name : names) {
            try (InputStream in = Migrations.class.getResourceAsStream("/db/migration/" + name)) {
                if (in == null) {
                    throw new IllegalStateException("Migrasi tidak ditemukan: " + name);
                }
                String sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                int version = Integer.parseInt(name.substring(1, name.indexOf("__")));
                list.add(new Migration(version, name, sql));
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
        list.sort(Comparator.comparingInt(Migration::version));
        return list;
    }

    private static String sha256(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static int currentVersion(Db db) {
        List<java.util.Map<String, Object>> rows = db.query("SELECT MAX(version) AS v FROM schema_migrations");
        if (rows.isEmpty() || rows.get(0).get("v") == null) {
            return 0;
        }
        return ((Number) rows.get(0).get("v")).intValue();
    }
}
