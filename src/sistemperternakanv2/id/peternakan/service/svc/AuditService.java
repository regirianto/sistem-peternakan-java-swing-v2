package id.peternakan.service.svc;

import id.peternakan.domain.Permissions;
import id.peternakan.service.db.Db;
import id.peternakan.service.security.SessionInfo;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class AuditService {
    private final Db db;

    public AuditService(Db db) {
        this.db = db;
    }

    public Map<String, Object> list(SessionInfo session, String q, String module, String from, String to,
                                    int page, int size) {
        session.require(Permissions.AUDIT_READ);
        StringBuilder sql = new StringBuilder("""
                SELECT id, occurred_at, user_id, username, module, action, entity, entity_id,
                       before_json, after_json, reason, correlation_id
                FROM activity_logs WHERE 1=1
                """);
        StringBuilder countSql = new StringBuilder("SELECT COUNT(*) AS n FROM activity_logs WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (q != null && !q.isBlank()) {
            String clause = " AND (username LIKE ? OR action LIKE ? OR entity_id LIKE ? OR reason LIKE ?)";
            sql.append(clause);
            countSql.append(clause);
            String like = "%" + q.trim() + "%";
            args.add(like);
            args.add(like);
            args.add(like);
            args.add(like);
        }
        if (module != null && !module.isBlank()) {
            sql.append(" AND module = ?");
            countSql.append(" AND module = ?");
            args.add(module);
        }
        if (from != null && !from.isBlank()) {
            sql.append(" AND occurred_at >= ?");
            countSql.append(" AND occurred_at >= ?");
            args.add(LocalDate.parse(from).atStartOfDay());
        }
        if (to != null && !to.isBlank()) {
            sql.append(" AND occurred_at <= ?");
            countSql.append(" AND occurred_at <= ?");
            args.add(LocalDate.parse(to).atTime(23, 59, 59));
        }
        sql.append(" ORDER BY id DESC LIMIT ? OFFSET ?");
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add((page - 1) * size);
        long total = db.count(countSql.toString(), args.toArray());
        return Map.of("items", db.query(sql.toString(), pageArgs.toArray()), "total", total);
    }

    public Map<String, Object> modules(SessionInfo session) {
        session.require(Permissions.AUDIT_READ);
        return Map.of("items", db.query("SELECT DISTINCT module FROM activity_logs ORDER BY module").stream()
                .map(r -> Db.str(r, "module")).toList());
    }
}
