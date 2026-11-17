package id.peternakan.service.support;

import id.peternakan.service.api.ApiException;
import id.peternakan.service.db.Db;

import java.math.BigDecimal;
import java.util.Map;

public final class Settings {
    private Settings() {
    }

    public static String get(Db db, String key, String fallback) {
        Map<String, Object> row = db.queryOne("SELECT setting_value FROM app_settings WHERE setting_key = ?", key);
        return row == null ? fallback : Db.str(row, "setting_value");
    }

    public static int getInt(Db db, String key, int fallback) {
        String v = get(db, key, null);
        try {
            return v == null ? fallback : Integer.parseInt(v);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public static BigDecimal getDecimal(Db db, String key, BigDecimal fallback) {
        String v = get(db, key, null);
        try {
            return v == null ? fallback : new BigDecimal(v);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public static void set(Db.Tx tx, String key, String value) {
        tx.update("""
                INSERT INTO app_settings (setting_key, setting_value) VALUES (?, ?)
                ON DUPLICATE KEY UPDATE setting_value = VALUES(setting_value)
                """, key, value);
    }

    public static Map<String, Object> all(Db db) {
        return db.query("SELECT setting_key, setting_value FROM app_settings ORDER BY setting_key").stream()
                .collect(java.util.stream.Collectors.toMap(
                        r -> Db.str(r, "setting_key"),
                        r -> Db.str(r, "setting_value"),
                        (a, b) -> b, java.util.LinkedHashMap::new));
    }

    public static void requirePositive(BigDecimal value, String field) {
        if (value == null || value.signum() <= 0) {
            throw ApiException.badRequest(field + " harus lebih dari 0.");
        }
    }
}
