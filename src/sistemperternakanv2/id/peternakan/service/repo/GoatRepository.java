package id.peternakan.service.repo;

import id.peternakan.domain.Money;
import id.peternakan.service.api.ApiException;
import id.peternakan.service.db.Db;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class GoatRepository {
    private final Db db;

    public GoatRepository(Db db) {
        this.db = db;
    }

    public List<Map<String, Object>> list(String q, String status, String health, String readiness,
                                          Long supplierId, Long penId, String sort, int page, int size,
                                          Long branchId) {
        StringBuilder sql = new StringBuilder("""
                SELECT g.id, g.code, g.jenis, g.gender, g.entry_date, g.entry_weight_kg, g.status, g.health_status,
                       g.sale_readiness, g.acquisition_cost, g.photo_attachment_id, g.estimated_age_months,
                       p.name AS supplier_name, pen.name AS pen_name, pen.id AS pen_id, b.name AS branch_name,
                       (SELECT w.weight_kg FROM weight_records w WHERE w.goat_id = g.id
                        ORDER BY w.measured_at DESC, w.id DESC LIMIT 1) AS latest_weight
                FROM goats g
                JOIN parties p ON p.id = g.supplier_id
                JOIN branches b ON b.id = g.branch_id
                LEFT JOIN pen_placements pp ON pp.goat_id = g.id AND pp.exited_at IS NULL
                LEFT JOIN pens pen ON pen.id = pp.pen_id
                WHERE 1=1
                """);
        List<Object> args = new ArrayList<>();
        if (q != null && !q.isBlank()) {
            sql.append(" AND (g.code LIKE ? OR g.jenis LIKE ? OR p.name LIKE ?)");
            String like = "%" + q.trim() + "%";
            args.add(like);
            args.add(like);
            args.add(like);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND g.status = ?");
            args.add(status);
        }
        if (health != null && !health.isBlank()) {
            sql.append(" AND g.health_status = ?");
            args.add(health);
        }
        if (readiness != null && !readiness.isBlank()) {
            sql.append(" AND g.sale_readiness = ?");
            args.add(readiness);
        }
        if (supplierId != null) {
            sql.append(" AND g.supplier_id = ?");
            args.add(supplierId);
        }
        if (penId != null) {
            sql.append(" AND pen.id = ?");
            args.add(penId);
        }
        if (branchId != null) {
            sql.append(" AND g.branch_id = ?");
            args.add(branchId);
        }
        String order = switch (sort == null ? "" : sort) {
            case "code" -> "g.code ASC";
            case "code_desc" -> "g.code DESC";
            case "weight" -> "latest_weight DESC";
            case "weight_asc" -> "latest_weight ASC";
            case "entry" -> "g.entry_date DESC";
            default -> "g.code ASC";
        };
        sql.append(" ORDER BY ").append(order).append(" LIMIT ? OFFSET ?");
        args.add(size);
        args.add((page - 1) * size);
        return db.query(sql.toString(), args.toArray());
    }

    public long count(String q, String status, String health, String readiness, Long supplierId, Long penId,
                      Long branchId) {
        StringBuilder sql = new StringBuilder("""
                SELECT COUNT(*) AS n FROM goats g
                JOIN parties p ON p.id = g.supplier_id
                LEFT JOIN pen_placements pp ON pp.goat_id = g.id AND pp.exited_at IS NULL
                LEFT JOIN pens pen ON pen.id = pp.pen_id
                WHERE 1=1
                """);
        List<Object> args = new ArrayList<>();
        if (q != null && !q.isBlank()) {
            sql.append(" AND (g.code LIKE ? OR g.jenis LIKE ? OR p.name LIKE ?)");
            String like = "%" + q.trim() + "%";
            args.add(like);
            args.add(like);
            args.add(like);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND g.status = ?");
            args.add(status);
        }
        if (health != null && !health.isBlank()) {
            sql.append(" AND g.health_status = ?");
            args.add(health);
        }
        if (readiness != null && !readiness.isBlank()) {
            sql.append(" AND g.sale_readiness = ?");
            args.add(readiness);
        }
        if (supplierId != null) {
            sql.append(" AND g.supplier_id = ?");
            args.add(supplierId);
        }
        if (penId != null) {
            sql.append(" AND pen.id = ?");
            args.add(penId);
        }
        if (branchId != null) {
            sql.append(" AND g.branch_id = ?");
            args.add(branchId);
        }
        return db.count(sql.toString(), args.toArray());
    }

    public Map<String, Object> find(long id) {
        Map<String, Object> goat = db.queryOne("""
                SELECT g.*, p.name AS supplier_name, pen.name AS pen_name, pen.id AS pen_id,
                       (SELECT w.weight_kg FROM weight_records w WHERE w.goat_id = g.id
                        ORDER BY w.measured_at DESC, w.id DESC LIMIT 1) AS latest_weight,
                       (SELECT w.measured_at FROM weight_records w WHERE w.goat_id = g.id
                        ORDER BY w.measured_at DESC, w.id DESC LIMIT 1) AS latest_weight_date,
                       (SELECT r.doc_no FROM receipt_items ri JOIN receipts r ON r.id = ri.receipt_id
                        WHERE ri.goat_id = g.id) AS receipt_doc_no,
                       (SELECT v.sale_doc_no FROM v_goat_sale_items v
                        WHERE v.goat_id = g.id ORDER BY v.sale_date DESC, v.sale_item_id DESC LIMIT 1) AS sale_doc_no
                FROM goats g
                JOIN parties p ON p.id = g.supplier_id
                LEFT JOIN pen_placements pp ON pp.goat_id = g.id AND pp.exited_at IS NULL
                LEFT JOIN pens pen ON pen.id = pp.pen_id
                WHERE g.id = ?
                """, id);
        if (goat == null) {
            throw ApiException.notFound("Kambing tidak ditemukan.");
        }
        return goat;
    }

    public Map<String, Object> history(long id) {
        Map<String, Object> out = new HashMap<>();
        out.put("weights", db.query("""
                SELECT w.id, w.measured_at, w.weight_kg, w.notes, u.full_name AS recorder
                FROM weight_records w LEFT JOIN users u ON u.id = w.user_id
                WHERE w.goat_id = ? ORDER BY w.measured_at DESC, w.id DESC
                """, id));
        out.put("health", db.query("""
                SELECT h.*, u.full_name AS recorder FROM health_records h
                LEFT JOIN users u ON u.id = h.recorder_id WHERE h.goat_id = ?
                ORDER BY h.occurred_at DESC, h.id DESC
                """, id));
        out.put("placements", db.query("""
                SELECT pp.id, pp.entered_at, pp.exited_at, pp.reason, p.code AS pen_code, p.name AS pen_name
                FROM pen_placements pp JOIN pens p ON p.id = pp.pen_id
                WHERE pp.goat_id = ? ORDER BY pp.entered_at DESC
                """, id));
        out.put("feed", db.query("""
                SELECT fm.id, fm.movement_date, fm.qty, fm.unit_cost, fm.total_cost, f.name AS feed_name, f.unit
                FROM feed_movements fm JOIN feed_items f ON f.id = fm.feed_item_id
                WHERE fm.goat_id = ? OR (fm.pen_id IN (SELECT pen_id FROM pen_placements WHERE goat_id = ?)
                     AND fm.goat_id IS NULL)
                ORDER BY fm.movement_date DESC, fm.id DESC LIMIT 100
                """, id, id));
        out.put("expenses", db.query("""
                SELECT e.id, e.doc_no, e.expense_date, e.amount, e.status, ec.name AS category_name
                FROM expenses e JOIN expense_categories ec ON ec.id = e.category_id
                WHERE e.direct_goat_id = ? ORDER BY e.expense_date DESC
                """, id));
        out.put("allocations", db.query("""
                SELECT ca.id, ab.period_start, ab.period_end, ab.version, ab.status, ca.days, ca.amount,
                       cs.source_type, ab.total_amount
                FROM cost_allocations ca JOIN allocation_batches ab ON ab.id = ca.batch_id
                JOIN cost_sources cs ON cs.id = ab.cost_source_id
                WHERE ca.goat_id = ? ORDER BY ab.period_start DESC, ab.id DESC
                """, id));
        out.put("sales", db.query("""
                SELECT s.id, s.doc_no, s.sale_date, s.status, s.payment_status, v.net, v.hpp,
                       si.weight_kg, si.rate, v.sale_type
                FROM v_goat_sale_items v
                JOIN sales s ON s.id = v.sale_id
                JOIN sale_items si ON si.id = v.sale_item_id
                WHERE v.goat_id = ? ORDER BY v.sale_date DESC, v.sale_item_id DESC
                """, id));
        return out;
    }

    public long insert(Db.Tx tx, String code, Long receiptItemId, long supplierId, String jenis, String gender,
                       LocalDate birthDate, Integer estimatedAge, LocalDate entryDate, BigDecimal entryWeight,
                       BigDecimal acquisitionCost, String physicalMarks, String notes, long branchId) {
        if (code == null || code.isBlank()) {
            throw ApiException.badRequest("Kode kambing wajib diisi.");
        }
        String normalizedJenis = normalizeJenis(jenis);
        if (entryWeight == null || entryWeight.signum() <= 0) {
            throw ApiException.badRequest("Berat masuk harus lebih dari 0 kg.");
        }
        if (tx.queryOne("SELECT id FROM goats WHERE code = ?", code.trim()) != null) {
            throw ApiException.conflict("Kode kambing sudah digunakan: " + code);
        }
        return tx.insert("""
                INSERT INTO goats (code, receipt_item_id, supplier_id, jenis, gender, birth_date, estimated_age_months,
                entry_date, entry_weight_kg, acquisition_cost, physical_marks, notes, branch_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, code.trim(), receiptItemId, supplierId, normalizedJenis, gender, birthDate, estimatedAge,
                entryDate, entryWeight, Money.nz(acquisitionCost), physicalMarks, notes, branchId);
    }

    private static String normalizeJenis(String jenis) {
        String value = jenis == null ? "" : jenis.trim().toLowerCase(java.util.Locale.ROOT);
        return switch (value) {
            case "", "kambing", "goat" -> "Kambing";
            case "domba", "sheep" -> "Domba";
            default -> throw ApiException.badRequest("Jenis ternak harus Kambing atau Domba.");
        };
    }

    public void insertWeight(Db.Tx tx, long goatId, LocalDate measuredAt, BigDecimal weight, long userId,
                             String notes) {
        if (weight == null || weight.signum() <= 0) {
            throw ApiException.badRequest("Berat harus lebih dari 0 kg.");
        }
        if (measuredAt == null) {
            throw ApiException.badRequest("Tanggal penimbangan wajib diisi.");
        }
        tx.insert("""
                INSERT INTO weight_records (goat_id, measured_at, weight_kg, user_id, notes) VALUES (?, ?, ?, ?, ?)
                """, goatId, measuredAt, weight, userId, notes);
    }

    public void insertHealth(Db.Tx tx, long goatId, LocalDate occurredAt, String complaint, String examination,
                             String action, String medication, BigDecimal cost, LocalDate followUp,
                             String responsible, long userId) {
        if (occurredAt == null) {
            throw ApiException.badRequest("Tanggal kejadian kesehatan wajib diisi.");
        }
        tx.insert("""
                INSERT INTO health_records (goat_id, occurred_at, complaint, examination, action, medication,
                cost, follow_up_date, responsible, recorder_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, goatId, occurredAt, complaint, examination, action, medication, Money.nz(cost),
                followUp, responsible, userId);
    }

    public void setReadiness(Db.Tx tx, long goatId, String readiness) {
        if (!"KARANTINA".equals(readiness) && !"SIAP_JUAL".equals(readiness)) {
            throw ApiException.badRequest("Kelayakan jual tidak dikenal.");
        }
        tx.update("UPDATE goats SET sale_readiness = ?, version = version + 1 WHERE id = ?", readiness, goatId);
    }

    public void setHealthStatus(Db.Tx tx, long goatId, String health) {
        if (!List.of("SEHAT", "SAKIT", "PEMULIHAN").contains(health)) {
            throw ApiException.badRequest("Status kesehatan tidak dikenal.");
        }
        tx.update("UPDATE goats SET health_status = ?, version = version + 1 WHERE id = ?", health, goatId);
    }

    public void setPhoto(Db.Tx tx, long goatId, Long attachmentId) {
        tx.update("UPDATE goats SET photo_attachment_id = ? WHERE id = ?", attachmentId, goatId);
    }

    /**
     * Hari-keberadaan inklusif. Perpindahan pada hari yang sama hanya dihitung pada kandang terakhir.
     */
    public long presenceDays(Db.Tx tx, long goatId, LocalDate start, LocalDate end, Long penScope) {
        Map<String, Object> goat = tx.queryOne("SELECT entry_date, death_date, status FROM goats WHERE id = ?", goatId);
        if (goat == null) {
            return 0;
        }
        LocalDate entry = Db.date(goat, "entry_date");
        LocalDate exit = Db.date(goat, "death_date");
        if (exit == null && "TERJUAL".equals(Db.str(goat, "status"))) {
            Map<String, Object> sale = tx.queryOne("""
                    SELECT MAX(v.sale_date) AS sale_date FROM v_goat_sale_items v WHERE v.goat_id = ?
                    """, goatId);
            exit = sale == null ? null : Db.date(sale, "sale_date");
        }
        if (exit == null && "DIPOTONG".equals(Db.str(goat, "status"))) {
            Map<String, Object> slaughter = tx.queryOne("""
                    SELECT slaughter_date FROM slaughter_orders
                    WHERE goat_id = ? AND status = 'POSTED' ORDER BY id DESC LIMIT 1
                    """, goatId);
            exit = slaughter == null ? null : Db.date(slaughter, "slaughter_date");
        }
        LocalDate from = start.isAfter(entry) ? start : entry;
        LocalDate to = end;
        if (exit != null && exit.isBefore(to)) {
            to = exit;
        }
        if (from.isAfter(to)) {
            return 0;
        }
        List<Map<String, Object>> placements = tx.query("""
                SELECT pen_id, entered_at, exited_at FROM pen_placements WHERE goat_id = ? ORDER BY entered_at
                """, goatId);
        long days = 0;
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            Long lastPen = null;
            LocalDateTime lastEntered = null;
            for (Map<String, Object> p : placements) {
                LocalDateTime entered = (LocalDateTime) p.get("entered_at");
                LocalDateTime exited = (LocalDateTime) p.get("exited_at");
                LocalDate eDate = entered.toLocalDate();
                LocalDate xDate = exited == null ? null : exited.toLocalDate();
                if (!d.isBefore(eDate) && (xDate == null || !d.isAfter(xDate))) {
                    if (lastEntered == null || entered.isAfter(lastEntered)) {
                        lastEntered = entered;
                        lastPen = Db.lng(p, "pen_id");
                    }
                }
            }
            if (penScope == null) {
                days++;
            } else if (lastPen != null && lastPen.equals(penScope)) {
                days++;
            }
        }
        return days;
    }

    public List<Map<String, Object>> sellable(Long branchId) {
        return db.query("""
                SELECT g.id, g.code, g.jenis, g.gender, g.entry_date, g.acquisition_cost,
                       (SELECT w.weight_kg FROM weight_records w WHERE w.goat_id = g.id
                        ORDER BY w.measured_at DESC, w.id DESC LIMIT 1) AS latest_weight,
                       pen.name AS pen_name
                FROM goats g
                LEFT JOIN pen_placements pp ON pp.goat_id = g.id AND pp.exited_at IS NULL
                LEFT JOIN pens pen ON pen.id = pp.pen_id
                WHERE g.status = 'AKTIF' AND g.sale_readiness = 'SIAP_JUAL'
                  AND NOT EXISTS (SELECT 1 FROM goat_sale_locks l WHERE l.goat_id = g.id)
                  AND NOT EXISTS (SELECT 1 FROM slaughter_orders so WHERE so.goat_id = g.id
                                  AND so.status <> 'DIBATALKAN')
                  AND (? IS NULL OR g.branch_id = ?)
                ORDER BY g.code
                """, branchId, branchId);
    }
}
