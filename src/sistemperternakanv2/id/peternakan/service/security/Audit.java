package id.peternakan.service.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import id.peternakan.service.api.Json;
import id.peternakan.service.db.Db;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Log aktivitas tidak dapat diubah/dihapus melalui aplikasi.
 * Untuk aktivitas transaksi penting, log ditulis pada transaksi database yang sama.
 */
public final class Audit {
    private static final ObjectMapper M = Json.MAPPER;

    private Audit() {
    }

    public static void log(Db.Tx tx, SessionInfo actor, String module, String action, String entity,
                           String entityId, Object before, Object after, String reason) {
        log(tx, actor.userId(), actor.username(), module, action, entity, entityId, before, after, reason, null);
    }

    public static void log(Db.Tx tx, SessionInfo actor, String module, String action, String entity,
                           String entityId, Object before, Object after, String reason, String correlationId) {
        log(tx, actor.userId(), actor.username(), module, action, entity, entityId, before, after, reason, correlationId);
    }

    public static void log(Db.Tx tx, long userId, String username, String module, String action, String entity,
                           String entityId, Object before, Object after, String reason, String correlationId) {
        try {
            String beforeJson = before == null ? null : M.writeValueAsString(before);
            String afterJson = after == null ? null : M.writeValueAsString(after);
            tx.update("""
                    INSERT INTO activity_logs (user_id, username, module, action, entity, entity_id,
                    before_json, after_json, reason, correlation_id)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, userId <= 0 ? null : userId, username, module, action, entity, entityId,
                    beforeJson, afterJson, reason, correlationId);
        } catch (Exception e) {
            throw new IllegalStateException("Gagal menulis audit: " + e.getMessage(), e);
        }
    }

    public static void logUser(Db db, long userId, String username, String module, String action,
                               String entity, String entityId, Object before, Object after,
                               String reason, String correlationId) {
        db.withTx(tx -> {
            log(tx, userId, username, module, action, entity, entityId, before, after, reason, correlationId);
            return null;
        });
    }

    public static void logAnonymous(Db db, String module, String action, String entity, String entityId,
                                    String reason, String correlationId) {
        db.withTx(tx -> {
            log(tx, 0, "ANONIM", module, action, entity, entityId, null,
                    Map.of("alasan", reason), reason, correlationId);
            return null;
        });
    }

    public static Map<String, Object> describe(Object value) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (value != null) {
            map.put("value", value);
        }
        return map;
    }
}
