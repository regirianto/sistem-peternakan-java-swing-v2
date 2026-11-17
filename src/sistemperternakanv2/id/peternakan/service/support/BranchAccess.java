package id.peternakan.service.support;

import id.peternakan.service.api.ApiException;
import id.peternakan.service.db.Db;
import id.peternakan.service.security.SessionInfo;

import java.util.List;
import java.util.Map;

/**
 * Pembatasan data per cabang. Owner bebas memilih semua cabang; pengguna lain
 * hanya cabang yang ditugaskan pada {@code user_branches}.
 */
public final class BranchAccess {
    private BranchAccess() {
    }

    /** Cabang yang boleh dilihat pengguna (owner: semua cabang aktif). */
    public static List<Map<String, Object>> accessible(Db db, SessionInfo session) {
        if (session.isOwner()) {
            return db.query("""
                    SELECT id, code, name, branch_type, address, phone, active
                    FROM branches WHERE active = 1 ORDER BY id
                    """);
        }
        return db.query("""
                SELECT b.id, b.code, b.name, b.branch_type, b.address, b.phone, b.active
                FROM branches b JOIN user_branches ub ON ub.branch_id = b.id
                WHERE ub.user_id = ? AND b.active = 1 ORDER BY b.id
                """, session.userId());
    }

    /**
     * Filter baca: {@code null} berarti semua cabang (khusus owner). Pengguna
     * lain selalu mendapat satu cabang yang boleh diakses.
     */
    public static Long filter(Db db, SessionInfo session, Long requested) {
        if (session.isOwner()) {
            if (requested == null) {
                return null;
            }
            requireExists(db, requested);
            return requested;
        }
        List<Long> allowed = allowedIds(db, session);
        if (allowed.isEmpty()) {
            throw ApiException.forbidden("Anda belum ditugaskan ke cabang mana pun.");
        }
        if (requested == null) {
            return allowed.get(0);
        }
        if (!allowed.contains(requested)) {
            throw ApiException.forbidden("Anda tidak memiliki akses ke cabang tersebut.");
        }
        return requested;
    }

    /** Cabang tujuan penulisan: wajib valid dan boleh diakses. */
    public static long requireWrite(Db db, SessionInfo session, Long requested) {
        if (session.isOwner()) {
            if (requested == null) {
                Map<String, Object> first = db.queryOne("""
                        SELECT id FROM branches WHERE active = 1 ORDER BY id LIMIT 1
                        """);
                if (first == null) {
                    throw ApiException.badRequest("Belum ada cabang aktif.");
                }
                return Db.lng(first, "id");
            }
            requireExists(db, requested);
            return requested;
        }
        Long resolved = filter(db, session, requested);
        return resolved;
    }

    public static void requireBranch(Db db, SessionInfo session, long branchId) {
        requireBranch(db, session, branchId, "Anda tidak memiliki akses ke cabang data ini.");
    }

    public static void requireBranch(Db db, SessionInfo session, long branchId, String message) {
        if (session.isOwner()) {
            return;
        }
        Map<String, Object> row = db.queryOne("""
                SELECT 1 AS x FROM user_branches WHERE user_id = ? AND branch_id = ?
                """, session.userId(), branchId);
        if (row == null) {
            throw ApiException.forbidden(message);
        }
    }

    public static List<Long> allowedIds(Db db, SessionInfo session) {
        return db.query("""
                SELECT b.id FROM branches b JOIN user_branches ub ON ub.branch_id = b.id
                WHERE ub.user_id = ? AND b.active = 1 ORDER BY b.id
                """, session.userId()).stream().map(r -> Db.lng(r, "id")).toList();
    }

    private static void requireExists(Db db, long branchId) {
        Map<String, Object> row = db.queryOne("SELECT id FROM branches WHERE id = ?", branchId);
        if (row == null) {
            throw ApiException.badRequest("Cabang tidak ditemukan.");
        }
    }
}
