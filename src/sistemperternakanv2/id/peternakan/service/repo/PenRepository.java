package id.peternakan.service.repo;

import id.peternakan.service.api.ApiException;
import id.peternakan.service.db.Db;

import java.util.List;
import java.util.Map;

public final class PenRepository {
    private final Db db;

    public PenRepository(Db db) {
        this.db = db;
    }

    public List<Map<String, Object>> list(Long branchId) {
        return db.query("""
                SELECT p.*, b.name AS branch_name,
                       (SELECT COUNT(*) FROM pen_placements pp WHERE pp.pen_id = p.id AND pp.exited_at IS NULL) AS occupancy
                FROM pens p JOIN branches b ON b.id = p.branch_id
                WHERE (? IS NULL OR p.branch_id = ?)
                ORDER BY b.name, p.code
                """, branchId, branchId);
    }

    public Map<String, Object> find(long id) {
        Map<String, Object> pen = db.queryOne("""
                SELECT p.*, (SELECT COUNT(*) FROM pen_placements pp WHERE pp.pen_id = p.id AND pp.exited_at IS NULL) AS occupancy
                FROM pens p WHERE p.id = ?
                """, id);
        if (pen == null) {
            throw ApiException.notFound("Kandang tidak ditemukan.");
        }
        return pen;
    }

    public long create(Db.Tx tx, long branchId, String code, String name, int capacity, String notes) {
        if (code == null || code.isBlank() || name == null || name.isBlank()) {
            throw ApiException.badRequest("Kode dan nama kandang wajib diisi.");
        }
        if (capacity <= 0) {
            throw ApiException.badRequest("Kapasitas kandang harus lebih dari 0.");
        }
        return tx.insert("""
                INSERT INTO pens (branch_id, code, name, capacity, notes) VALUES (?, ?, ?, ?, ?)
                """, branchId, code.trim(), name.trim(), capacity, notes);
    }

    public void update(Db.Tx tx, long id, String name, int capacity, String status, String notes) {
        if (capacity <= 0) {
            throw ApiException.badRequest("Kapasitas kandang harus lebih dari 0.");
        }
        tx.update("""
                UPDATE pens SET name = ?, capacity = ?, status = ?, notes = ?, version = version + 1 WHERE id = ?
                """, name, capacity, status, notes, id);
    }

    public Map<String, Object> currentPlacement(Db.Tx tx, long goatId) {
        return tx.queryOne("""
                SELECT pp.*, p.name AS pen_name, p.code AS pen_code FROM pen_placements pp
                JOIN pens p ON p.id = pp.pen_id
                WHERE pp.goat_id = ? AND pp.exited_at IS NULL ORDER BY pp.entered_at DESC LIMIT 1
                """, goatId);
    }

    public void ensureCapacity(Db.Tx tx, long penId) {
        Map<String, Object> pen = tx.queryOne("SELECT * FROM pens WHERE id = ? FOR UPDATE", penId);
        if (pen == null) {
            throw ApiException.notFound("Kandang tidak ditemukan.");
        }
        if (!"AKTIF".equals(Db.str(pen, "status"))) {
            throw ApiException.conflict("Kandang " + Db.str(pen, "name") + " tidak aktif.");
        }
        long occupancy = Db.lng(tx.queryOne("""
                SELECT COUNT(*) AS n FROM pen_placements WHERE pen_id = ? AND exited_at IS NULL
                """, penId), "n");
        int capacity = ((Number) pen.get("capacity")).intValue();
        if (occupancy >= capacity) {
            throw ApiException.conflict("Kandang " + Db.str(pen, "name") + " sudah penuh ("
                    + occupancy + "/" + capacity + ").");
        }
    }

    public long insertPlacement(Db.Tx tx, long goatId, long penId, java.time.LocalDateTime enteredAt,
                                long userId, String reason) {
        return tx.insert("""
                INSERT INTO pen_placements (goat_id, pen_id, entered_at, reason, created_by)
                VALUES (?, ?, ?, ?, ?)
                """, goatId, penId, enteredAt, reason, userId);
    }

    public void closePlacement(Db.Tx tx, long goatId, java.time.LocalDateTime exitedAt, String reason) {
        tx.update("""
                UPDATE pen_placements SET exited_at = ?, reason = CONCAT(COALESCE(reason,''), ' ', COALESCE(?, ''))
                WHERE goat_id = ? AND exited_at IS NULL
                """, exitedAt, reason, goatId);
    }
}
