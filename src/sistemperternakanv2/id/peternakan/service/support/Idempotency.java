package id.peternakan.service.support;

import id.peternakan.service.api.ApiException;
import id.peternakan.service.api.Json;
import id.peternakan.service.db.Db;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Idempotensi perintah posting. Request ID yang sama mengembalikan hasil yang sama
 * tanpa membuat dokumen kedua.
 */
public final class Idempotency {
    private Idempotency() {
    }

    public static Map<String, Object> run(Db.Tx tx, String requestId, long actorId, String commandType,
                                          Supplier<Map<String, Object>> work) {
        if (requestId == null || requestId.isBlank()) {
            throw ApiException.badRequest("Request ID wajib dikirim untuk perintah posting.");
        }
        Map<String, Object> existing = tx.queryOne("""
                SELECT response_json FROM processed_requests WHERE request_id = ? FOR UPDATE
                """, requestId);
        if (existing != null) {
            try {
                Object value = Json.MAPPER.readValue(Db.str(existing, "response_json"), Object.class);
                if (value instanceof Map<?, ?> map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> result = (Map<String, Object>) map;
                    result.put("idempotentReplay", true);
                    return result;
                }
            } catch (Exception e) {
                throw new ApiException(500, "Gagal membaca hasil request sebelumnya.");
            }
        }
        Map<String, Object> result = work.get();
        try {
            tx.update("""
                    INSERT INTO processed_requests (request_id, actor_id, command_type, result_reference, response_json)
                    VALUES (?, ?, ?, ?, ?)
                    """, requestId, actorId, commandType, String.valueOf(result.get("docNo")),
                    Json.MAPPER.writeValueAsString(result));
        } catch (Exception e) {
            throw new ApiException(409, "Request ID sudah digunakan untuk perintah lain.");
        }
        return result;
    }
}
