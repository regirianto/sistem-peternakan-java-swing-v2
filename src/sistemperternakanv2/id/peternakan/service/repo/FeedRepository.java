package id.peternakan.service.repo;

import id.peternakan.domain.AllocationCalculator;
import id.peternakan.domain.Money;
import id.peternakan.service.api.ApiException;
import id.peternakan.service.db.Db;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class FeedRepository {
    private final Db db;

    public FeedRepository(Db db) {
        this.db = db;
    }

    public List<Map<String, Object>> items(Long branchId) {
        return db.query("""
                SELECT f.*, b.name AS branch_name FROM feed_items f
                JOIN branches b ON b.id = f.branch_id
                WHERE (? IS NULL OR f.branch_id = ?) ORDER BY b.name, f.code
                """, branchId, branchId);
    }

    public Map<String, Object> findItem(long id) {
        Map<String, Object> item = db.queryOne("SELECT * FROM feed_items WHERE id = ?", id);
        if (item == null) {
            throw ApiException.notFound("Item pakan tidak ditemukan.");
        }
        return item;
    }

    public long createItem(Db.Tx tx, long branchId, String code, String name, String unit, BigDecimal minStock) {
        if (code == null || code.isBlank() || name == null || name.isBlank()) {
            throw ApiException.badRequest("Kode dan nama pakan wajib diisi.");
        }
        return tx.insert("""
                INSERT INTO feed_items (branch_id, code, name, unit, min_stock) VALUES (?, ?, ?, ?, ?)
                """, branchId, code.trim(), name.trim(), unit == null || unit.isBlank() ? "kg" : unit,
                Money.nz(minStock));
    }

    public Map<String, Object> receive(Db.Tx tx, long itemId, LocalDate date, BigDecimal qty, BigDecimal unitCost,
                                       String notes, long userId) {
        if (qty == null || qty.signum() <= 0) {
            throw ApiException.badRequest("Jumlah pakan masuk harus lebih dari 0.");
        }
        if (unitCost == null || unitCost.signum() <= 0) {
            throw ApiException.badRequest("Biaya satuan pakan harus lebih dari 0.");
        }
        Map<String, Object> item = tx.queryOne("SELECT * FROM feed_items WHERE id = ? FOR UPDATE", itemId);
        if (item == null) {
            throw ApiException.notFound("Item pakan tidak ditemukan.");
        }
        BigDecimal oldQty = Db.dec(item, "stock_qty");
        BigDecimal oldAvg = Db.dec(item, "avg_cost");
        BigDecimal newAvg = AllocationCalculator.weightedAverage(oldQty, oldAvg, qty, unitCost);
        BigDecimal total = Money.multiply(qty, unitCost);
        long movementId = tx.insert("""
                INSERT INTO feed_movements (feed_item_id, movement_type, movement_date, qty, unit_cost, total_cost,
                notes, created_by) VALUES (?, 'MASUK', ?, ?, ?, ?, ?, ?)
                """, itemId, date, qty, unitCost, total, notes, userId);
        tx.update("UPDATE feed_items SET stock_qty = stock_qty + ?, avg_cost = ? WHERE id = ?",
                qty, newAvg, itemId);
        return Map.of("movementId", movementId, "total", total, "newAvg", newAvg);
    }

    public Map<String, Object> usage(Db.Tx tx, long itemId, LocalDate date, BigDecimal qty, Long penId,
                                     Long goatId, LocalDate serviceStart, LocalDate serviceEnd, String notes,
                                     long userId) {
        if (qty == null || qty.signum() <= 0) {
            throw ApiException.badRequest("Jumlah pemakaian harus lebih dari 0.");
        }
        Map<String, Object> item = tx.queryOne("SELECT * FROM feed_items WHERE id = ? FOR UPDATE", itemId);
        if (item == null) {
            throw ApiException.notFound("Item pakan tidak ditemukan.");
        }
        BigDecimal stock = Db.dec(item, "stock_qty");
        if (stock.compareTo(qty) < 0) {
            throw ApiException.conflict("Stok " + Db.str(item, "name") + " tidak cukup. Stok tersedia "
                    + stock.stripTrailingZeros().toPlainString() + " " + Db.str(item, "unit") + ".");
        }
        BigDecimal unitCost = Db.dec(item, "avg_cost");
        BigDecimal total = Money.multiply(qty, unitCost);
        long movementId = tx.insert("""
                INSERT INTO feed_movements (feed_item_id, movement_type, movement_date, qty, unit_cost, total_cost,
                pen_id, goat_id, service_start, service_end, notes, created_by)
                VALUES (?, 'PEMAKAIAN', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, itemId, date, qty, unitCost, total, penId, goatId,
                serviceStart == null ? date : serviceStart, serviceEnd == null ? date : serviceEnd, notes, userId);
        tx.update("UPDATE feed_items SET stock_qty = stock_qty - ? WHERE id = ?", qty, itemId);
        return Map.of("movementId", movementId, "total", total, "unitCost", unitCost);
    }

    public List<Map<String, Object>> movements(Long itemId, int limit, Long branchId) {
        if (itemId == null) {
            return db.query("""
                    SELECT fm.*, f.name AS feed_name, f.unit, pen.name AS pen_name, g.code AS goat_code
                    FROM feed_movements fm JOIN feed_items f ON f.id = fm.feed_item_id
                    LEFT JOIN pens pen ON pen.id = fm.pen_id LEFT JOIN goats g ON g.id = fm.goat_id
                    WHERE (? IS NULL OR f.branch_id = ?)
                    ORDER BY fm.movement_date DESC, fm.id DESC LIMIT ?
                    """, branchId, branchId, limit);
        }
        return db.query("""
                SELECT fm.*, f.name AS feed_name, f.unit, pen.name AS pen_name, g.code AS goat_code
                FROM feed_movements fm JOIN feed_items f ON f.id = fm.feed_item_id
                LEFT JOIN pens pen ON pen.id = fm.pen_id LEFT JOIN goats g ON g.id = fm.goat_id
                WHERE fm.feed_item_id = ? AND (? IS NULL OR f.branch_id = ?)
                ORDER BY fm.movement_date DESC, fm.id DESC LIMIT ?
                """, itemId, branchId, branchId, limit);
    }

    public BigDecimal stockValue(Long branchId) {
        return Db.dec(db.queryOne("""
                SELECT COALESCE(SUM(stock_qty * avg_cost),0) AS v FROM feed_items
                WHERE (? IS NULL OR branch_id = ?)
                """, branchId, branchId), "v");
    }
}
