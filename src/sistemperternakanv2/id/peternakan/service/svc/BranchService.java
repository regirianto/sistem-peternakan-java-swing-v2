package id.peternakan.service.svc;

import id.peternakan.domain.Permissions;
import id.peternakan.service.api.ApiException;
import id.peternakan.service.db.Db;
import id.peternakan.service.repo.BranchRepository;
import id.peternakan.service.security.Audit;
import id.peternakan.service.security.SessionInfo;
import id.peternakan.service.support.BranchAccess;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class BranchService {
    private final Db db;
    private final BranchRepository repo;

    public BranchService(Db db, BranchRepository repo) {
        this.db = db;
        this.repo = repo;
    }

    /** Cabang yang boleh dipakai pengguna (untuk selector). */
    public Map<String, Object> accessible(SessionInfo session) {
        return Map.of("items", BranchAccess.accessible(db, session));
    }

    public Map<String, Object> all(SessionInfo session) {
        session.require(Permissions.BRANCH_MANAGE);
        return Map.of("items", repo.listAll());
    }

    public Map<String, Object> create(SessionInfo session, Map<String, Object> body) {
        session.require(Permissions.BRANCH_MANAGE);
        String type = normalizeType(PartyService.str(body, "type"));
        long id = db.withTx(tx -> {
            long newId = repo.create(tx, PartyService.str(body, "code"), PartyService.str(body, "name"),
                    type, PartyService.str(body, "address"), PartyService.str(body, "phone"));
            Audit.log(tx, session, "BRANCHES", "CREATE", "branches", String.valueOf(newId), null, body, null);
            return newId;
        });
        return Map.of("id", id);
    }

    public Map<String, Object> update(SessionInfo session, long id, Map<String, Object> body) {
        session.require(Permissions.BRANCH_MANAGE);
        Map<String, Object> before = repo.find(id);
        boolean active = body.get("active") == null || PartyService.bool(body, "active", true);
        db.withTx(tx -> {
            repo.update(tx, id, PartyService.str(body, "name"), normalizeType(PartyService.str(body, "type")),
                    PartyService.str(body, "address"), PartyService.str(body, "phone"), active);
            Audit.log(tx, session, "BRANCHES", "UPDATE", "branches", String.valueOf(id), before, body, null);
            return null;
        });
        return Map.of("id", id, "updated", true);
    }

    public Map<String, Object> assignUserBranches(SessionInfo session, long userId, Map<String, Object> body) {
        session.require(Permissions.BRANCH_MANAGE);
        Map<String, Object> user = db.queryOne("""
                SELECT u.id, r.code AS role_code FROM users u JOIN roles r ON r.id = u.role_id
                WHERE u.id = ?
                """, userId);
        if (user == null) {
            throw ApiException.notFound("Pengguna tidak ditemukan.");
        }
        List<Long> branchIds = new ArrayList<>();
        Object raw = body.get("branchIds");
        if (raw instanceof List<?> list) {
            for (Object value : list) {
                branchIds.add(Long.parseLong(String.valueOf(value)));
            }
        }
        for (Long branchId : branchIds) {
            if (db.queryOne("SELECT id FROM branches WHERE id = ?", branchId) == null) {
                throw ApiException.badRequest("Cabang tidak ditemukan: " + branchId);
            }
        }
        if (!"OWNER".equals(Db.str(user, "role_code")) && branchIds.isEmpty()) {
            throw ApiException.badRequest("Pengguna non-owner minimal harus memiliki satu cabang.");
        }
        db.withTx(tx -> {
            repo.replaceUserBranches(tx, userId, branchIds);
            Audit.log(tx, session, "BRANCHES", "ASSIGN_USER", "users", String.valueOf(userId), null,
                    Map.of("branchIds", branchIds), null);
            return null;
        });
        return Map.of("id", userId, "branchIds", branchIds);
    }

    public List<Long> userBranches(long userId) {
        return repo.branchIdsOfUser(userId);
    }

    private String normalizeType(String type) {
        if (type == null || type.isBlank()) {
            return "CABANG";
        }
        String value = type.trim().toUpperCase();
        if (!List.of("PUSAT", "CABANG").contains(value)) {
            throw ApiException.badRequest("Tipe cabang harus PUSAT atau CABANG.");
        }
        return value;
    }
}
