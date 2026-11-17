package id.peternakan.service.repo;

import id.peternakan.service.api.ApiException;
import id.peternakan.service.db.Db;

import java.util.List;
import java.util.Map;

public final class BranchRepository {
    private final Db db;

    public BranchRepository(Db db) {
        this.db = db;
    }

    public List<Map<String, Object>> listAll() {
        return db.query("""
                SELECT b.*,
                       (SELECT COUNT(*) FROM pens p WHERE p.branch_id = b.id) AS pen_count,
                       (SELECT COUNT(*) FROM goats g WHERE g.branch_id = b.id AND g.status = 'AKTIF') AS goat_count,
                       (SELECT COUNT(*) FROM user_branches ub WHERE ub.branch_id = b.id) AS user_count
                FROM branches b ORDER BY b.id
                """);
    }

    public Map<String, Object> find(long id) {
        Map<String, Object> branch = db.queryOne("SELECT * FROM branches WHERE id = ?", id);
        if (branch == null) {
            throw ApiException.notFound("Cabang tidak ditemukan.");
        }
        return branch;
    }

    public long create(Db.Tx tx, String code, String name, String type, String address, String phone) {
        if (code == null || code.isBlank() || name == null || name.isBlank()) {
            throw ApiException.badRequest("Kode dan nama cabang wajib diisi.");
        }
        return tx.insert("""
                INSERT INTO branches (code, name, branch_type, address, phone) VALUES (?, ?, ?, ?, ?)
                """, code.trim().toUpperCase(), name.trim(), type, address, phone);
    }

    public void update(Db.Tx tx, long id, String name, String type, String address, String phone,
                       boolean active) {
        tx.update("""
                UPDATE branches SET name = ?, branch_type = ?, address = ?, phone = ?, active = ?,
                version = version + 1 WHERE id = ?
                """, name, type, address, phone, active ? 1 : 0, id);
    }

    public List<Long> branchIdsOfUser(long userId) {
        return db.query("SELECT branch_id FROM user_branches WHERE user_id = ? ORDER BY branch_id", userId)
                .stream().map(r -> Db.lng(r, "branch_id")).toList();
    }

    public void replaceUserBranches(Db.Tx tx, long userId, List<Long> branchIds) {
        tx.update("DELETE FROM user_branches WHERE user_id = ?", userId);
        for (Long branchId : branchIds) {
            tx.update("INSERT INTO user_branches (user_id, branch_id) VALUES (?, ?)", userId, branchId);
        }
    }
}
