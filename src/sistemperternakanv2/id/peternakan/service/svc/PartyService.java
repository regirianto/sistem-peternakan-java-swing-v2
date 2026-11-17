package id.peternakan.service.svc;

import id.peternakan.domain.Permissions;
import id.peternakan.service.api.ApiException;
import id.peternakan.service.db.Db;
import id.peternakan.service.repo.PartyRepository;
import id.peternakan.service.security.Audit;
import id.peternakan.service.security.SessionInfo;

import java.util.List;
import java.util.Map;

public final class PartyService {
    private final Db db;
    private final PartyRepository repo;

    public PartyService(Db db, PartyRepository repo) {
        this.db = db;
        this.repo = repo;
    }

    public Map<String, Object> list(SessionInfo session, String role, String q, int page, int size) {
        session.require(Permissions.SUPPLIER_MANAGE);
        return Map.of("items", repo.list(role, q, page, size), "total", repo.count(role, q));
    }

    public Map<String, Object> find(long id) {
        Map<String, Object> party = repo.find(id);
        party.put("purchases", repo.purchases(id));
        party.put("sales", repo.sales(id));
        return party;
    }

    public Map<String, Object> create(SessionInfo session, Map<String, Object> body) {
        String role = rolePermission(session, body);
        List<String> roles = roles(body);
        long id = db.withTx(tx -> {
            long newId = repo.create(tx, str(body, "code"), str(body, "name"), str(body, "phone"),
                    str(body, "address"), bool(body, "active", true), str(body, "notes"), roles);
            Audit.log(tx, session, "PARTIES", "CREATE", "parties", String.valueOf(newId), null,
                    Map.of("name", str(body, "name"), "roles", roles), null);
            return newId;
        });
        return Map.of("id", id);
    }

    public Map<String, Object> update(SessionInfo session, long id, Map<String, Object> body) {
        Map<String, Object> before = repo.find(id);
        List<String> roles = roles(body);
        db.withTx(tx -> {
            repo.update(tx, id, str(body, "name"), str(body, "phone"), str(body, "address"),
                    bool(body, "active", true), str(body, "notes"), roles);
            Audit.log(tx, session, "PARTIES", "UPDATE", "parties", String.valueOf(id), before,
                    Map.of("name", str(body, "name"), "roles", roles), null);
            return null;
        });
        return Map.of("id", id, "updated", true);
    }

    private String rolePermission(SessionInfo session, Map<String, Object> body) {
        List<String> roles = roles(body);
        if (roles.contains("SUPPLIER")) {
            session.require(Permissions.SUPPLIER_MANAGE);
        }
        if (roles.contains("CUSTOMER")) {
            session.require(Permissions.CUSTOMER_MANAGE);
        }
        if (roles.isEmpty()) {
            throw ApiException.badRequest("Pilih minimal satu peran supplier atau pelanggan.");
        }
        return roles.get(0);
    }

    private List<String> roles(Map<String, Object> body) {
        Object raw = body.get("roles");
        if (raw instanceof List<?> list) {
            return list.stream().map(String::valueOf).filter(v -> List.of("SUPPLIER", "CUSTOMER").contains(v)).toList();
        }
        String role = str(body, "role");
        return role == null ? List.of() : List.of(role);
    }

    static String str(Map<String, Object> body, String key) {
        Object v = body.get(key);
        return v == null ? null : String.valueOf(v);
    }

    static Long longVal(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (v == null || String.valueOf(v).isBlank()) {
            return null;
        }
        return Long.parseLong(String.valueOf(v));
    }

    static boolean bool(Map<String, Object> body, String key, boolean fallback) {
        Object v = body.get(key);
        if (v == null) {
            return fallback;
        }
        return v instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(v));
    }
}
