package id.peternakan.service.repo;

import id.peternakan.domain.Money;
import id.peternakan.service.api.ApiException;
import id.peternakan.service.db.Db;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Pemotongan kambing dan persediaan daging. Satu pemotongan menghasilkan satu
 * batch daging dengan HPP per kg yang dibekukan saat posting.
 */
public final class MeatRepository {
    /** Toleransi selisih berat daging + tulang terhadap karkas (susut trimming). */
    private static final BigDecimal TRIM_TOLERANCE = new BigDecimal("0.02");

    private final Db db;

    public MeatRepository(Db db) {
        this.db = db;
    }

    public List<Map<String, Object>> listOrders(String q, String status, int page, int size, Long branchId) {
        StringBuilder sql = new StringBuilder("""
                SELECT so.id, so.doc_no, so.slaughter_date, so.status, so.live_weight_kg,
                       so.carcass_weight_kg, so.meat_weight_kg, so.bone_weight_kg, so.dressing_pct,
                       so.meat_yield_pct, so.total_cost, so.hpp_per_kg, g.code AS goat_code,
                       mb.qty_remaining_kg, mb.status AS batch_status, b.name AS branch_name
                FROM slaughter_orders so
                JOIN branches b ON b.id = so.branch_id
                JOIN goats g ON g.id = so.goat_id
                LEFT JOIN meat_batches mb ON mb.slaughter_id = so.id
                WHERE 1=1
                """);
        List<Object> args = new ArrayList<>();
        if (q != null && !q.isBlank()) {
            sql.append(" AND (so.doc_no LIKE ? OR g.code LIKE ?)");
            String like = "%" + q.trim() + "%";
            args.add(like);
            args.add(like);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND so.status = ?");
            args.add(status);
        }
        if (branchId != null) {
            sql.append(" AND so.branch_id = ?");
            args.add(branchId);
        }
        sql.append(" ORDER BY so.slaughter_date DESC, so.id DESC LIMIT ? OFFSET ?");
        args.add(size);
        args.add((page - 1) * size);
        return db.query(sql.toString(), args.toArray());
    }

    public long countOrders(String q, String status, Long branchId) {
        StringBuilder sql = new StringBuilder("""
                SELECT COUNT(*) AS n FROM slaughter_orders so JOIN goats g ON g.id = so.goat_id WHERE 1=1
                """);
        List<Object> args = new ArrayList<>();
        if (q != null && !q.isBlank()) {
            sql.append(" AND (so.doc_no LIKE ? OR g.code LIKE ?)");
            String like = "%" + q.trim() + "%";
            args.add(like);
            args.add(like);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND so.status = ?");
            args.add(status);
        }
        if (branchId != null) {
            sql.append(" AND so.branch_id = ?");
            args.add(branchId);
        }
        return db.count(sql.toString(), args.toArray());
    }

    public Map<String, Object> findOrder(long id) {
        Map<String, Object> order = db.queryOne("""
                SELECT so.*, g.code AS goat_code, g.jenis, g.gender, g.entry_weight_kg,
                       u.full_name AS created_by_name, b.name AS branch_name
                FROM slaughter_orders so
                JOIN goats g ON g.id = so.goat_id
                JOIN branches b ON b.id = so.branch_id
                LEFT JOIN users u ON u.id = so.created_by
                WHERE so.id = ?
                """, id);
        if (order == null) {
            throw ApiException.notFound("Pemotongan tidak ditemukan.");
        }
        order.put("batch", db.queryOne("SELECT * FROM meat_batches WHERE slaughter_id = ?", id));
        order.put("movements", db.query("""
                SELECT mm.*, s.doc_no AS sale_doc_no
                FROM meat_movements mm
                LEFT JOIN sales s ON s.id = mm.sale_id
                WHERE mm.batch_id = (SELECT id FROM meat_batches WHERE slaughter_id = ?)
                ORDER BY mm.movement_date, mm.id
                """, id));
        return order;
    }

    public long saveDraft(Db.Tx tx, Long id, String docNo, long branchId, long goatId, LocalDate date,
                          BigDecimal liveWeight, BigDecimal carcassWeight, BigDecimal meatWeight,
                          BigDecimal boneWeight, BigDecimal offalWeight, BigDecimal processingCost,
                          String notes, long userId) {
        if (date == null) {
            throw ApiException.badRequest("Tanggal potong wajib diisi.");
        }
        requirePositive(liveWeight, "Berat hidup");
        requirePositive(carcassWeight, "Berat karkas");
        requirePositive(meatWeight, "Berat daging");
        BigDecimal bone = Money.nz(boneWeight);
        if (bone.signum() < 0) {
            throw ApiException.badRequest("Berat tulang tidak boleh negatif.");
        }
        if (carcassWeight.compareTo(liveWeight) > 0) {
            throw ApiException.badRequest("Berat karkas tidak boleh melebihi berat hidup.");
        }
        BigDecimal sum = meatWeight.add(bone);
        BigDecimal tolerance = carcassWeight.multiply(TRIM_TOLERANCE).setScale(3, RoundingMode.HALF_UP);
        if (sum.subtract(carcassWeight).abs().compareTo(tolerance) > 0) {
            throw ApiException.badRequest("Berat daging + tulang (" + sum.stripTrailingZeros().toPlainString()
                    + " kg) harus sama dengan berat karkas (" + carcassWeight.stripTrailingZeros().toPlainString()
                    + " kg), toleransi " + tolerance.stripTrailingZeros().toPlainString() + " kg.");
        }
        if (offalWeight != null && offalWeight.signum() < 0) {
            throw ApiException.badRequest("Berat jeroan tidak boleh negatif.");
        }
        BigDecimal processing = Money.nz(processingCost);
        if (processing.signum() < 0) {
            throw ApiException.badRequest("Biaya potong tidak boleh negatif.");
        }
        Map<String, Object> goat = tx.queryOne("SELECT * FROM goats WHERE id = ? FOR UPDATE", goatId);
        if (goat == null) {
            throw ApiException.notFound("Kambing tidak ditemukan.");
        }
        if (Db.lng(goat, "branch_id") != branchId) {
            throw ApiException.conflict("Kambing " + Db.str(goat, "code") + " berada di cabang lain.");
        }
        if (!"AKTIF".equals(Db.str(goat, "status"))) {
            throw ApiException.conflict("Kambing " + Db.str(goat, "code") + " tidak aktif.");
        }
        if (!"SIAP_JUAL".equals(Db.str(goat, "sale_readiness"))) {
            throw ApiException.conflict("Kambing " + Db.str(goat, "code") + " belum layak potong (karantina).");
        }
        if (tx.queryOne("SELECT 1 AS x FROM goat_sale_locks WHERE goat_id = ?", goatId) != null) {
            throw ApiException.conflict("Kambing " + Db.str(goat, "code") + " sudah dipakai pada penjualan lain.");
        }
        Map<String, Object> active = tx.queryOne("""
                SELECT id FROM slaughter_orders WHERE goat_id = ? AND status <> 'DIBATALKAN' AND id <> ?
                """, goatId, id == null ? 0L : id);
        if (active != null) {
            throw ApiException.conflict("Kambing " + Db.str(goat, "code") + " sudah memiliki pemotongan aktif.");
        }

        BigDecimal dressing = percentage(carcassWeight, liveWeight);
        BigDecimal meatYield = percentage(meatWeight, carcassWeight);
        BigDecimal bonePct = percentage(bone, carcassWeight);

        if (id == null) {
            return tx.insert("""
                    INSERT INTO slaughter_orders (doc_no, branch_id, goat_id, slaughter_date, live_weight_kg,
                    carcass_weight_kg, meat_weight_kg, bone_weight_kg, offal_weight_kg, dressing_pct,
                    meat_yield_pct, bone_pct, processing_cost, notes, status, created_by)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'DRAFT', ?)
                    """, docNo, branchId, goatId, date, liveWeight, carcassWeight, meatWeight, bone, offalWeight,
                    dressing, meatYield, bonePct, processing, notes, userId);
        }
        Map<String, Object> existing = tx.queryOne("SELECT * FROM slaughter_orders WHERE id = ? FOR UPDATE", id);
        if (existing == null) {
            throw ApiException.notFound("Pemotongan tidak ditemukan.");
        }
        if (!"DRAFT".equals(Db.str(existing, "status"))) {
            throw ApiException.conflict("Hanya draft pemotongan yang dapat diubah.");
        }
        if (Db.lng(existing, "branch_id") != branchId) {
            throw ApiException.badRequest("Cabang pemotongan tidak dapat diubah.");
        }
        tx.update("""
                UPDATE slaughter_orders SET goat_id = ?, slaughter_date = ?, live_weight_kg = ?,
                carcass_weight_kg = ?, meat_weight_kg = ?, bone_weight_kg = ?, offal_weight_kg = ?,
                dressing_pct = ?, meat_yield_pct = ?, bone_pct = ?, processing_cost = ?,
                notes = ?, version = version + 1 WHERE id = ?
                """, goatId, date, liveWeight, carcassWeight, meatWeight, bone, offalWeight,
                dressing, meatYield, bonePct, processing, notes, id);
        return id;
    }

    public Map<String, Object> lockOrder(Db.Tx tx, long id) {
        Map<String, Object> order = tx.queryOne("SELECT * FROM slaughter_orders WHERE id = ? FOR UPDATE", id);
        if (order == null) {
            throw ApiException.notFound("Pemotongan tidak ditemukan.");
        }
        return order;
    }

    public List<Map<String, Object>> batches(String q, boolean openOnly, Long branchId) {
        StringBuilder sql = new StringBuilder("""
                SELECT mb.*, g.code AS goat_code, so.doc_no AS slaughter_doc_no, so.slaughter_date,
                       b.name AS branch_name
                FROM meat_batches mb
                JOIN goats g ON g.id = mb.goat_id
                JOIN slaughter_orders so ON so.id = mb.slaughter_id
                JOIN branches b ON b.id = mb.branch_id
                WHERE 1=1
                """);
        List<Object> args = new ArrayList<>();
        if (openOnly) {
            sql.append(" AND mb.status = 'OPEN' AND mb.qty_remaining_kg > 0");
        }
        if (q != null && !q.isBlank()) {
            sql.append(" AND (mb.batch_no LIKE ? OR g.code LIKE ?)");
            String like = "%" + q.trim() + "%";
            args.add(like);
            args.add(like);
        }
        if (branchId != null) {
            sql.append(" AND mb.branch_id = ?");
            args.add(branchId);
        }
        sql.append(" ORDER BY mb.received_date, mb.id");
        return db.query(sql.toString(), args.toArray());
    }

    public List<Map<String, Object>> movements(Long batchId, int limit, Long branchId) {
        String base = """
                SELECT mm.*, mb.batch_no, g.code AS goat_code, s.doc_no AS sale_doc_no
                FROM meat_movements mm
                JOIN meat_batches mb ON mb.id = mm.batch_id
                JOIN goats g ON g.id = mb.goat_id
                LEFT JOIN sales s ON s.id = mm.sale_id
                """;
        if (batchId == null) {
            return db.query(base + " WHERE (? IS NULL OR mb.branch_id = ?)"
                    + " ORDER BY mm.movement_date DESC, mm.id DESC LIMIT ?", branchId, branchId, limit);
        }
        return db.query(base + " WHERE mm.batch_id = ? AND (? IS NULL OR mb.branch_id = ?)"
                + " ORDER BY mm.movement_date DESC, mm.id DESC LIMIT ?", batchId, branchId, branchId, limit);
    }

    public BigDecimal stockValue(Long branchId) {
        return Db.dec(db.queryOne("""
                SELECT COALESCE(SUM(qty_remaining_kg * hpp_per_kg),0) AS v FROM meat_batches
                WHERE (? IS NULL OR branch_id = ?)
                """, branchId, branchId), "v");
    }

    private static BigDecimal percentage(BigDecimal part, BigDecimal base) {
        if (base == null || base.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return part.multiply(new BigDecimal("100")).divide(base, 2, RoundingMode.HALF_UP);
    }

    private static void requirePositive(BigDecimal value, String field) {
        if (value == null || value.signum() <= 0) {
            throw ApiException.badRequest(field + " harus lebih dari 0.");
        }
    }
}
