package id.peternakan.service.svc;

import id.peternakan.domain.Money;
import id.peternakan.domain.Permissions;
import id.peternakan.service.api.ApiException;
import id.peternakan.service.db.Db;
import id.peternakan.service.repo.MeatRepository;
import id.peternakan.service.security.Audit;
import id.peternakan.service.security.SessionInfo;
import id.peternakan.service.support.BranchAccess;
import id.peternakan.service.support.DocNo;
import id.peternakan.service.support.Idempotency;
import id.peternakan.service.support.Journal;
import id.peternakan.service.support.PeriodGuard;
import id.peternakan.service.support.Settings;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Pemotongan kambing, persediaan daging, susut, dan harga daging per kg. */
public final class MeatService {
    private final Db db;
    private final MeatRepository repo;

    public MeatService(Db db, MeatRepository repo) {
        this.db = db;
        this.repo = repo;
    }

    public Map<String, Object> orders(SessionInfo session, String q, String status, int page, int size,
                                      Long branchId) {
        session.require(Permissions.MEAT_VIEW);
        Long filter = BranchAccess.filter(db, session, branchId);
        List<Map<String, Object>> items = repo.listOrders(q, status, page, size, filter);
        if (!session.has(Permissions.FINANCE_VIEW)) {
            for (Map<String, Object> item : items) {
                item.remove("total_cost");
                item.remove("hpp_per_kg");
            }
        }
        return Map.of("items", items, "total", repo.countOrders(q, status, filter));
    }

    public Map<String, Object> find(SessionInfo session, long id) {
        session.require(Permissions.MEAT_VIEW);
        Map<String, Object> order = repo.findOrder(id);
        BranchAccess.requireBranch(db, session, Db.lng(order, "branch_id"));
        if (!session.has(Permissions.FINANCE_VIEW)) {
            order.remove("total_cost");
            order.remove("hpp_per_kg");
            order.remove("goat_cost");
            if (order.get("batch") instanceof Map<?, ?> batch) {
                @SuppressWarnings("unchecked")
                Map<String, Object> batchMap = (Map<String, Object>) batch;
                batchMap.remove("hpp_per_kg");
            }
            if (order.get("movements") instanceof List<?> movements) {
                for (Object value : movements) {
                    if (value instanceof Map<?, ?> movement) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> movementMap = (Map<String, Object>) movement;
                        movementMap.remove("unit_cost");
                        movementMap.remove("total_cost");
                    }
                }
            }
        }
        return order;
    }

    public Map<String, Object> saveDraft(SessionInfo session, Long id, Map<String, Object> body) {
        session.require(Permissions.SLAUGHTER_MANAGE);
        long goatId = Long.parseLong(String.valueOf(body.get("goatId")));
        LocalDate date = LocalDate.parse(String.valueOf(body.get("slaughterDate")));
        BigDecimal live = Money.quantity(String.valueOf(body.get("liveWeight")));
        BigDecimal carcass = Money.quantity(String.valueOf(body.get("carcassWeight")));
        BigDecimal meat = Money.quantity(String.valueOf(body.get("meatWeight")));
        BigDecimal bone = Money.quantity(String.valueOf(body.getOrDefault("boneWeight", "0")));
        BigDecimal offal = body.get("offalWeight") == null || String.valueOf(body.get("offalWeight")).isBlank()
                ? null : Money.quantity(String.valueOf(body.get("offalWeight")));
        BigDecimal processing = Money.of(String.valueOf(body.getOrDefault("processingCost", "0")));
        Map<String, Object> goatBranch = db.queryOne("SELECT branch_id FROM goats WHERE id = ?", goatId);
        if (goatBranch == null) {
            throw ApiException.notFound("Kambing tidak ditemukan.");
        }
        long branchId = Db.lng(goatBranch, "branch_id");
        BranchAccess.requireBranch(db, session, branchId);
        return db.withTx(tx -> {
            PeriodGuard.requireOpen(tx, date);
            String docNo = id == null ? DocNo.next(tx, "PT", date) : null;
            long newId = repo.saveDraft(tx, id, docNo, branchId, goatId, date, live, carcass, meat, bone, offal,
                    processing, PartyService.str(body, "notes"), session.userId());
            Audit.log(tx, session, "SLAUGHTER", id == null ? "CREATE_DRAFT" : "UPDATE_DRAFT", "slaughter_orders",
                    String.valueOf(newId), null,
                    Map.of("goatId", goatId, "meatWeight", meat, "processingCost", processing), null);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", newId);
            if (docNo != null) {
                result.put("docNo", docNo);
            }
            return result;
        });
    }

    public Map<String, Object> post(SessionInfo session, long id, String requestId) {
        session.require(Permissions.SLAUGHTER_MANAGE);
        return db.withTx(tx -> Idempotency.run(tx, requestId, session.userId(), "SLAUGHTER_POST", () -> {
            Map<String, Object> order = repo.lockOrder(tx, id);
            BranchAccess.requireBranch(db, session, Db.lng(order, "branch_id"));
            if (!"DRAFT".equals(Db.str(order, "status"))) {
                throw ApiException.conflict("Hanya draft pemotongan yang dapat diposting.");
            }
            LocalDate date = Db.date(order, "slaughter_date");
            PeriodGuard.requireOpen(tx, date);
            long goatId = Db.lng(order, "goat_id");
            Map<String, Object> goat = tx.queryOne("SELECT * FROM goats WHERE id = ? FOR UPDATE", goatId);
            if (goat == null) {
                throw ApiException.conflict("Kambing pemotongan tidak ditemukan.");
            }
            if (!"AKTIF".equals(Db.str(goat, "status"))) {
                throw ApiException.conflict("Kambing " + Db.str(goat, "code") + " tidak lagi aktif.");
            }
            if (!"SIAP_JUAL".equals(Db.str(goat, "sale_readiness"))) {
                throw ApiException.conflict("Kambing " + Db.str(goat, "code") + " belum layak potong.");
            }
            if (tx.queryOne("SELECT 1 AS x FROM goat_sale_locks WHERE goat_id = ?", goatId) != null) {
                throw ApiException.conflict("Kambing " + Db.str(goat, "code") + " sudah dipakai pada penjualan lain.");
            }
            BigDecimal goatCost = Db.dec(goat, "acquisition_cost");
            BigDecimal processing = Db.dec(order, "processing_cost");
            BigDecimal total = goatCost.add(processing);
            BigDecimal meatWeight = Db.dec(order, "meat_weight_kg");
            if (meatWeight.signum() <= 0) {
                throw ApiException.conflict("Berat daging harus lebih dari 0.");
            }
            BigDecimal hppPerKg = total.divide(meatWeight, 4, RoundingMode.HALF_UP);
            String batchNo = DocNo.next(tx, "DG", date);
            long batchId = tx.insert("""
                    INSERT INTO meat_batches (batch_no, branch_id, slaughter_id, goat_id, qty_in_kg,
                    qty_remaining_kg, hpp_per_kg, received_date, status)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'OPEN')
                    """, batchNo, Db.lng(order, "branch_id"), id, goatId, meatWeight, meatWeight, hppPerKg, date);
            tx.update("""
                    UPDATE slaughter_orders SET status = 'POSTED', goat_cost = ?, total_cost = ?, hpp_per_kg = ?,
                    confirmed_at = NOW() WHERE id = ?
                    """, goatCost, total, hppPerKg, id);
            tx.update("""
                    UPDATE goats SET status = 'DIPOTONG', version = version + 1 WHERE id = ?
                    """, goatId);
            tx.update("""
                    UPDATE pen_placements SET exited_at = ? WHERE goat_id = ? AND exited_at IS NULL
                    """, date.atTime(23, 59, 59), goatId);

            List<Journal.Line> lines = new ArrayList<>();
            lines.add(Journal.Line.debit("1303", total,
                    "Persediaan daging " + Db.str(order, "doc_no")));
            if (goatCost.signum() > 0) {
                lines.add(Journal.Line.credit("1301", goatCost, "Persediaan kambing dipotong"));
            }
            if (processing.signum() > 0) {
                lines.add(Journal.Line.credit("1101", processing, "Biaya potong"));
            }
            Journal.post(tx, date, Db.lng(order, "branch_id"), "SLAUGHTER", id, "SLAUGHTER:" + id,
                    "Pemotongan " + Db.str(order, "doc_no"), session.userId(), lines);

            Audit.log(tx, session, "SLAUGHTER", "POST", "slaughter_orders", String.valueOf(id),
                    Map.of("status", "DRAFT"),
                    Map.of("status", "POSTED", "batchNo", batchNo, "hppPerKg", hppPerKg, "totalCost", total), null);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", id);
            result.put("docNo", Db.str(order, "doc_no"));
            result.put("status", "POSTED");
            result.put("batchNo", batchNo);
            result.put("batchId", batchId);
            result.put("hppPerKg", hppPerKg);
            result.put("totalCost", total);
            return result;
        }));
    }

    public Map<String, Object> cancel(SessionInfo session, long id, String reason, String requestId) {
        session.require(Permissions.SLAUGHTER_MANAGE);
        if (reason == null || reason.isBlank()) {
            throw ApiException.badRequest("Alasan pembatalan wajib diisi.");
        }
        return db.withTx(tx -> Idempotency.run(tx, requestId, session.userId(), "SLAUGHTER_CANCEL", () -> {
            Map<String, Object> order = repo.lockOrder(tx, id);
            BranchAccess.requireBranch(db, session, Db.lng(order, "branch_id"));
            String status = Db.str(order, "status");
            if ("DIBATALKAN".equals(status)) {
                return Map.<String, Object>of("id", id, "status", "DIBATALKAN");
            }
            LocalDate date = LocalDate.now();
            PeriodGuard.requireOpen(tx, date);
            if ("POSTED".equals(status)) {
                Map<String, Object> batch = tx.queryOne(
                        "SELECT * FROM meat_batches WHERE slaughter_id = ? FOR UPDATE", id);
                if (batch != null) {
                    long blocked = Db.lng(tx.queryOne("""
                            SELECT COUNT(*) AS n FROM meat_movements
                            WHERE batch_id = ? AND movement_type <> 'MASUK'
                            """, Db.lng(batch, "id")), "n");
                    if (blocked > 0) {
                        throw ApiException.conflict(
                                "Batch daging sudah memiliki penjualan atau susut. Batalkan transaksi tersebut dahulu.");
                    }
                    tx.update("DELETE FROM meat_batches WHERE id = ?", Db.lng(batch, "id"));
                }
                List<Map<String, Object>> entries = tx.query("""
                        SELECT id FROM journal_entries WHERE source_type = 'SLAUGHTER' AND source_id = ?
                        AND status = 'POSTED' ORDER BY id
                        """, id);
                for (Map<String, Object> entry : entries) {
                    Journal.reverse(tx, Db.lng(entry, "id"), date, session.userId(), reason);
                }
                tx.update("""
                        UPDATE goats SET status = 'AKTIF', sale_readiness = 'SIAP_JUAL', version = version + 1
                        WHERE id = ?
                        """, Db.lng(order, "goat_id"));
                Map<String, Object> placement = tx.queryOne("""
                        SELECT id FROM pen_placements WHERE goat_id = ? ORDER BY entered_at DESC LIMIT 1
                        """, Db.lng(order, "goat_id"));
                if (placement != null) {
                    tx.update("UPDATE pen_placements SET exited_at = NULL WHERE id = ?", Db.lng(placement, "id"));
                }
            }
            tx.update("UPDATE slaughter_orders SET status = 'DIBATALKAN' WHERE id = ?", id);
            Audit.log(tx, session, "SLAUGHTER", "CANCEL", "slaughter_orders", String.valueOf(id),
                    Map.of("status", status), Map.of("status", "DIBATALKAN"), reason);
            return Map.<String, Object>of("id", id, "status", "DIBATALKAN");
        }));
    }

    public Map<String, Object> batches(SessionInfo session, String q, boolean openOnly, Long branchId) {
        session.require(Permissions.MEAT_VIEW);
        Long filter = BranchAccess.filter(db, session, branchId);
        Map<String, Object> out = new LinkedHashMap<>();
        List<Map<String, Object>> items = repo.batches(q, openOnly, filter);
        if (!session.has(Permissions.FINANCE_VIEW)) {
            for (Map<String, Object> item : items) {
                item.remove("hpp_per_kg");
            }
        }
        out.put("items", items);
        if (session.has(Permissions.FINANCE_VIEW) || session.has(Permissions.SALE_MANAGE)) {
            out.put("pricePerKg", pricePerKg());
        }
        if (session.has(Permissions.FINANCE_VIEW)) {
            out.put("stockValue", repo.stockValue(filter));
        }
        return out;
    }

    public Map<String, Object> movements(SessionInfo session, Long batchId, Long branchId) {
        session.require(Permissions.MEAT_VIEW);
        Long filter = BranchAccess.filter(db, session, branchId);
        return Map.of("items", repo.movements(batchId, 300, filter));
    }

    public Map<String, Object> waste(SessionInfo session, Map<String, Object> body, String requestId) {
        session.require(Permissions.SLAUGHTER_MANAGE);
        long batchId = Long.parseLong(String.valueOf(body.get("batchId")));
        LocalDate date = LocalDate.parse(String.valueOf(body.get("movementDate")));
        BigDecimal qty = Money.quantity(String.valueOf(body.get("qty")));
        String notes = PartyService.str(body, "notes");
        return db.withTx(tx -> Idempotency.run(tx, requestId, session.userId(), "MEAT_WASTE", () -> {
            PeriodGuard.requireOpen(tx, date);
            if (qty.signum() <= 0) {
                throw ApiException.badRequest("Jumlah susut harus lebih dari 0.");
            }
            Map<String, Object> batch = tx.queryOne("""
                    SELECT mb.*, g.code AS goat_code FROM meat_batches mb
                    JOIN goats g ON g.id = mb.goat_id WHERE mb.id = ? FOR UPDATE
                    """, batchId);
            if (batch == null) {
                throw ApiException.notFound("Batch daging tidak ditemukan.");
            }
            BranchAccess.requireBranch(db, session, Db.lng(batch, "branch_id"));
            BigDecimal remaining = Db.dec(batch, "qty_remaining_kg");
            if (remaining.compareTo(qty) < 0) {
                throw ApiException.conflict("Sisa daging batch " + Db.str(batch, "batch_no") + " hanya "
                        + remaining.stripTrailingZeros().toPlainString() + " kg.");
            }
            BigDecimal unitCost = Db.dec(batch, "hpp_per_kg");
            BigDecimal total = Money.multiply(qty, unitCost);
            long movementId = tx.insert("""
                    INSERT INTO meat_movements (batch_id, movement_type, movement_date, qty_kg, unit_cost,
                    total_cost, notes, created_by) VALUES (?, 'SUSUT', ?, ?, ?, ?, ?, ?)
                    """, batchId, date, qty, unitCost, total, notes, session.userId());
            BigDecimal newRemaining = remaining.subtract(qty);
            tx.update("""
                    UPDATE meat_batches SET qty_remaining_kg = ?,
                    status = CASE WHEN ? <= 0 THEN 'CLOSED' ELSE status END WHERE id = ?
                    """, newRemaining, newRemaining, batchId);
            Journal.post(tx, date, Db.lng(batch, "branch_id"), "MEAT_WASTE", batchId, "MEAT_WASTE:" + movementId,
                    "Susut daging " + Db.str(batch, "batch_no"), session.userId(), List.of(
                            Journal.Line.debit("6104", total, "Susut persediaan daging"),
                            Journal.Line.credit("1303", total, "Persediaan daging berkurang")));
            Audit.log(tx, session, "MEAT", "WASTE", "meat_batches", String.valueOf(batchId), null,
                    Map.of("qty", qty, "total", total, "notes", notes == null ? "" : notes), notes);
            return Map.of("movementId", movementId, "total", total, "remainingKg", newRemaining);
        }));
    }

    public BigDecimal pricePerKg() {
        return Settings.getDecimal(db, "meat.price_per_kg", BigDecimal.ZERO);
    }
}
