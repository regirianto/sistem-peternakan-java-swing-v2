package id.peternakan.service.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import id.peternakan.service.config.AppConfig;
import id.peternakan.service.api.ApiException;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Akses JDBC dengan prepared statement dan transaksi eksplisit.
 * Hasil query dikembalikan sebagai Map dengan nama kolom snake_case.
 */
public final class Db implements AutoCloseable {

    @FunctionalInterface
    public interface TxWork<T> {
        T run(Tx tx);
    }

    private final HikariDataSource ds;

    public Db(AppConfig config) {
        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl(config.dbUrl());
        hc.setUsername(config.dbUser());
        hc.setPassword(config.dbPassword());
        hc.setMaximumPoolSize(10);
        hc.setMinimumIdle(2);
        hc.setPoolName("peternakan-pool");
        hc.setConnectionTimeout(10000);
        this.ds = new HikariDataSource(hc);
    }

    public <T> T withTx(TxWork<T> work) {
        try (Connection c = ds.getConnection()) {
            boolean oldAuto = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                T result = work.run(new Tx(c));
                c.commit();
                return result;
            } catch (RuntimeException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(oldAuto);
            }
        } catch (SQLException e) {
            throw new ApiException(500, "Kesalahan database: " + e.getMessage());
        }
    }

    public List<Map<String, Object>> query(String sql, Object... args) {
        try (Connection c = ds.getConnection()) {
            return Tx.query(c, sql, args);
        } catch (SQLException e) {
            throw new ApiException(500, "Kesalahan database: " + e.getMessage());
        }
    }

    public Map<String, Object> queryOne(String sql, Object... args) {
        List<Map<String, Object>> rows = query(sql, args);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public long count(String sql, Object... args) {
        Map<String, Object> row = queryOne(sql, args);
        if (row == null || row.values().isEmpty()) {
            return 0;
        }
        Object v = row.values().iterator().next();
        return v == null ? 0 : ((Number) v).longValue();
    }

    public Connection rawConnection() throws SQLException {
        return ds.getConnection();
    }

    public String jdbcUrl() {
        return ds.getJdbcUrl();
    }

    public String user() {
        return ds.getUsername();
    }

    public String password() {
        return ds.getPassword();
    }

    @Override
    public void close() {
        ds.close();
    }

    /** Wrapper transaksi. */
    public static final class Tx {
        private final Connection c;

        Tx(Connection c) {
            this.c = c;
        }

        public Connection connection() {
            return c;
        }

        public List<Map<String, Object>> query(String sql, Object... args) {
            try {
                return Tx.query(c, sql, args);
            } catch (SQLException e) {
                throw new ApiException(500, "Kesalahan query: " + e.getMessage());
            }
        }

        public Map<String, Object> queryOne(String sql, Object... args) {
            List<Map<String, Object>> rows = query(sql, args);
            return rows.isEmpty() ? null : rows.get(0);
        }

        public int update(String sql, Object... args) {
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                bind(ps, args);
                return ps.executeUpdate();
            } catch (SQLException e) {
                throw new ApiException(409, "Gagal menyimpan data: " + e.getMessage());
            }
        }

        public long insert(String sql, Object... args) {
            try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                bind(ps, args);
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        return keys.getLong(1);
                    }
                }
                // Pernyataan tanpa kolom AUTO_INCREMENT (mis. tabel kunci) tidak
                // menghasilkan ID; kembalikan 0 dan biarkan pemanggil tidak memakai ID.
                return 0;
            } catch (SQLException e) {
                throw new ApiException(409, "Gagal menyimpan data: " + e.getMessage());
            }
        }

        private static List<Map<String, Object>> query(Connection c, String sql, Object... args) throws SQLException {
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                bind(ps, args);
                try (ResultSet rs = ps.executeQuery()) {
                    ResultSetMetaData md = rs.getMetaData();
                    int cols = md.getColumnCount();
                    List<Map<String, Object>> rows = new ArrayList<>();
                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        for (int i = 1; i <= cols; i++) {
                            row.put(md.getColumnLabel(i), convert(rs.getObject(i)));
                        }
                        rows.add(row);
                    }
                    return rows;
                }
            }
        }

        private static void bind(PreparedStatement ps, Object... args) throws SQLException {
            if (args == null) {
                return;
            }
            for (int i = 0; i < args.length; i++) {
                Object a = args[i];
                if (a == null) {
                    ps.setObject(i + 1, null);
                } else if (a instanceof LocalDate d) {
                    ps.setDate(i + 1, Date.valueOf(d));
                } else if (a instanceof LocalDateTime dt) {
                    ps.setTimestamp(i + 1, Timestamp.valueOf(dt));
                } else if (a instanceof BigDecimal bd) {
                    ps.setBigDecimal(i + 1, bd);
                } else if (a instanceof Enum<?> e) {
                    ps.setString(i + 1, e.name());
                } else {
                    ps.setObject(i + 1, a);
                }
            }
        }

        private static Object convert(Object value) throws SQLException {
            if (value instanceof Date d) {
                return d.toLocalDate();
            }
            if (value instanceof Timestamp t) {
                return t.toLocalDateTime();
            }
            return value;
        }
    }

    /** Util untuk membaca nilai dari Map hasil query. */
    public static String str(Map<String, Object> row, String key) {
        Object v = row == null ? null : row.get(key);
        return v == null ? null : String.valueOf(v);
    }

    public static long lng(Map<String, Object> row, String key) {
        Object v = row == null ? null : row.get(key);
        if (v == null) {
            return 0;
        }
        if (v instanceof Boolean b) {
            return b ? 1 : 0;
        }
        return ((Number) v).longValue();
    }

    public static BigDecimal dec(Map<String, Object> row, String key) {
        Object v = row == null ? null : row.get(key);
        if (v == null) {
            return BigDecimal.ZERO;
        }
        return v instanceof BigDecimal b ? b : new BigDecimal(String.valueOf(v));
    }

    public static LocalDate date(Map<String, Object> row, String key) {
        Object v = row == null ? null : row.get(key);
        if (v == null) {
            return null;
        }
        if (v instanceof LocalDate d) {
            return d;
        }
        if (v instanceof LocalDateTime dt) {
            return dt.toLocalDate();
        }
        String text = String.valueOf(v);
        try {
            return text.length() >= 10 ? LocalDate.parse(text.substring(0, 10)) : LocalDate.parse(text);
        } catch (java.time.format.DateTimeParseException e) {
            throw new IllegalStateException("Format tanggal tidak dapat dibaca: " + text);
        }
    }

    /** Konversi DATETIME/Timestamp/ISO string dari JDBC ke LocalDateTime. */
    public static LocalDateTime dateTime(Map<String, Object> row, String key) {
        Object v = row == null ? null : row.get(key);
        if (v == null) {
            return null;
        }
        if (v instanceof LocalDateTime dt) {
            return dt;
        }
        if (v instanceof java.sql.Timestamp ts) {
            return ts.toLocalDateTime();
        }
        if (v instanceof LocalDate d) {
            return d.atStartOfDay();
        }
        String text = String.valueOf(v);
        try {
            return text.length() >= 10 ? LocalDateTime.parse(text) : LocalDate.parse(text).atStartOfDay();
        } catch (java.time.format.DateTimeParseException e) {
            return LocalDate.parse(text.substring(0, 10)).atStartOfDay();
        }
    }
}
