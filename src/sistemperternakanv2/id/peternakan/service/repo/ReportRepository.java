package id.peternakan.service.repo;

import id.peternakan.domain.Money;
import id.peternakan.service.db.Db;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ReportRepository {
    private final Db db;

    public ReportRepository(Db db) {
        this.db = db;
    }

    public Map<String, Object> dashboard(LocalDate from, LocalDate to, Long branchId) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("activeGoats", countGoats("status = 'AKTIF'", branchId));
        out.put("readyGoats", countGoats("status = 'AKTIF' AND sale_readiness = 'SIAP_JUAL'", branchId));
        out.put("quarantineGoats", countGoats("status = 'AKTIF' AND sale_readiness = 'KARANTINA'", branchId));
        out.put("sickGoats", countGoats("status = 'AKTIF' AND health_status <> 'SEHAT'", branchId));
        out.put("soldGoats", countGoats("status = 'TERJUAL'", branchId));
        out.put("slaughteredGoats", countGoats("status = 'DIPOTONG'", branchId));
        out.put("deadGoats", countGoats("status = 'MATI'", branchId));
        out.put("draftSlaughter", branchCount("slaughter_orders", "status = 'DRAFT'", branchId));
        out.put("meatStockKg", Db.dec(db.queryOne("""
                SELECT COALESCE(SUM(qty_remaining_kg),0) AS v FROM meat_batches
                WHERE (? IS NULL OR branch_id = ?)
                """, branchId, branchId), "v"));
        out.put("meatStockValue", Db.dec(db.queryOne("""
                SELECT COALESCE(SUM(qty_remaining_kg * hpp_per_kg),0) AS v FROM meat_batches
                WHERE (? IS NULL OR branch_id = ?)
                """, branchId, branchId), "v"));
        out.put("draftPurchases", branchCount("purchases", "status = 'DRAFT'", branchId));
        out.put("draftSales", branchCount("sales", "status = 'DRAFT'", branchId));
        out.put("pendingApprovals", db.count("""
                SELECT COUNT(*) FROM sales s JOIN sale_approvals sa ON sa.sale_id = s.id
                WHERE s.status='DRAFT' AND s.approved_by IS NULL AND sa.approved_at IS NULL
                  AND (? IS NULL OR s.branch_id = ?)
                """, branchId, branchId));
        out.put("nearFullPens", db.query("""
                SELECT p.id, p.code, p.name, p.capacity,
                       (SELECT COUNT(*) FROM pen_placements pp WHERE pp.pen_id = p.id AND pp.exited_at IS NULL) AS occupancy
                FROM pens p WHERE p.status = 'AKTIF' AND (? IS NULL OR p.branch_id = ?)
                HAVING occupancy >= capacity * 0.8 ORDER BY occupancy DESC
                """, branchId, branchId));
        out.put("latestWeights", db.query("""
                SELECT g.code, w.weight_kg, w.measured_at, u.full_name AS recorder
                FROM weight_records w JOIN goats g ON g.id = w.goat_id
                LEFT JOIN users u ON u.id = w.user_id
                WHERE (? IS NULL OR g.branch_id = ?)
                ORDER BY w.measured_at DESC, w.id DESC LIMIT 10
                """, branchId, branchId));
        out.put("recentHealth", db.query("""
                SELECT g.code, h.occurred_at, h.complaint, h.action, g.health_status
                FROM health_records h JOIN goats g ON g.id = h.goat_id
                WHERE (? IS NULL OR g.branch_id = ?)
                ORDER BY h.occurred_at DESC, h.id DESC LIMIT 10
                """, branchId, branchId));
        out.put("feedLowStock", db.query("""
                SELECT id, code, name, unit, stock_qty, min_stock FROM feed_items
                WHERE stock_qty <= min_stock AND (? IS NULL OR branch_id = ?) ORDER BY name
                """, branchId, branchId));
        out.put("periodStart", from);
        out.put("periodEnd", to);
        out.put("revenue", Db.dec(db.queryOne("""
                SELECT COALESCE(SUM(si.net),0) AS v FROM sale_items si JOIN sales s ON s.id = si.sale_id
                WHERE s.status IN ('DIKONFIRMASI','SELESAI') AND s.sale_date BETWEEN ? AND ?
                  AND (? IS NULL OR s.branch_id = ?)
                """, from, to, branchId, branchId), "v"));
        out.put("hpp", Db.dec(db.queryOne("""
                SELECT COALESCE(SUM(si.hpp),0) AS v FROM sale_items si JOIN sales s ON s.id = si.sale_id
                WHERE s.status IN ('DIKONFIRMASI','SELESAI') AND s.sale_date BETWEEN ? AND ?
                  AND (? IS NULL OR s.branch_id = ?)
                """, from, to, branchId, branchId), "v"));
        out.put("expenses", expensesTotal(from, to, branchId));
        out.put("feedUsage", feedUsageTotal(from, to, branchId));
        out.put("losses", lossesTotal(from, to, branchId));
        BigDecimal result = Db.dec(out, "revenue").subtract(Db.dec(out, "hpp"))
                .subtract(Db.dec(out, "expenses")).subtract(Db.dec(out, "feedUsage")).subtract(Db.dec(out, "losses"));
        out.put("periodResult", Money.nz(result));
        out.put("receivables", totalRemaining("""
                SELECT COALESCE(SUM(x.remaining),0) AS v FROM (
                  SELECT s.total_net - COALESCE((SELECT SUM(pa.amount) FROM payment_allocations pa
                    JOIN payments p ON p.id=pa.payment_id AND p.status='POSTED' WHERE pa.sale_id=s.id),0) AS remaining
                  FROM sales s WHERE s.status IN ('DIKONFIRMASI','SELESAI')
                    AND (? IS NULL OR s.branch_id = ?)) x WHERE x.remaining > 0
                """, branchId, branchId));
        out.put("payables", totalRemaining("""
                SELECT COALESCE(SUM(x.remaining),0) AS v FROM (
                  SELECT COALESCE((SELECT SUM(ri.acquisition_cost) FROM receipt_items ri
                    JOIN receipts r ON r.id=ri.receipt_id WHERE r.purchase_id=p.id),0)
                    - COALESCE((SELECT SUM(pa.amount) FROM payment_allocations pa
                    JOIN payments pay ON pay.id=pa.payment_id AND pay.status='POSTED' WHERE pa.purchase_id=p.id),0) AS remaining
                  FROM purchases p WHERE p.status IN ('DITERIMA_SEBAGIAN','SELESAI')
                    AND (? IS NULL OR p.branch_id = ?)) x WHERE x.remaining > 0
                """, branchId, branchId));
        return out;
    }

    public BigDecimal expensesTotal(LocalDate from, LocalDate to, Long branchId) {
        return Db.dec(db.queryOne("""
                SELECT COALESCE(SUM(e.amount),0) AS v FROM expenses e
                JOIN expense_categories ec ON ec.id = e.category_id
                WHERE e.status = 'POSTED' AND ec.treatment IN ('OPERASIONAL','PENJUALAN')
                  AND e.expense_date BETWEEN ? AND ? AND (? IS NULL OR e.branch_id = ?)
                """, from, to, branchId, branchId), "v");
    }

    public BigDecimal feedUsageTotal(LocalDate from, LocalDate to, Long branchId) {
        return Db.dec(db.queryOne("""
                SELECT COALESCE(SUM(fm.total_cost),0) AS v FROM feed_movements fm
                JOIN feed_items f ON f.id = fm.feed_item_id
                WHERE fm.movement_type = 'PEMAKAIAN' AND fm.movement_date BETWEEN ? AND ?
                  AND (? IS NULL OR f.branch_id = ?)
                """, from, to, branchId, branchId), "v");
    }

    public BigDecimal lossesTotal(LocalDate from, LocalDate to, Long branchId) {
        BigDecimal writeoffs = Db.dec(db.queryOne("""
                SELECT COALESCE(SUM(w.value),0) AS v FROM inventory_writeoffs w
                JOIN goats g ON g.id = w.goat_id
                WHERE w.writeoff_date BETWEEN ? AND ? AND (? IS NULL OR g.branch_id = ?)
                """, from, to, branchId, branchId), "v");
        BigDecimal meatWaste = Db.dec(db.queryOne("""
                SELECT COALESCE(SUM(mm.total_cost),0) AS v FROM meat_movements mm
                JOIN meat_batches mb ON mb.id = mm.batch_id
                WHERE mm.movement_type = 'SUSUT' AND mm.movement_date BETWEEN ? AND ?
                  AND (? IS NULL OR mb.branch_id = ?)
                """, from, to, branchId, branchId), "v");
        return writeoffs.add(meatWaste);
    }

    private long countGoats(String where, Long branchId) {
        return db.count("SELECT COUNT(*) FROM goats WHERE " + where + " AND (? IS NULL OR branch_id = ?)",
                branchId, branchId);
    }

    private long branchCount(String table, String where, Long branchId) {
        return db.count("SELECT COUNT(*) FROM " + table + " WHERE " + where
                + " AND (? IS NULL OR branch_id = ?)", branchId, branchId);
    }

    private BigDecimal totalRemaining(String sql, Object... args) {
        return Db.dec(db.queryOne(sql, args), "v");
    }

    public Map<String, Object> periodProfitLoss(LocalDate from, LocalDate to, Long branchId) {
        Map<String, Object> out = new LinkedHashMap<>();
        BigDecimal revenue = Db.dec(db.queryOne("""
                SELECT COALESCE(SUM(si.net),0) AS v FROM sale_items si JOIN sales s ON s.id=si.sale_id
                WHERE s.status IN ('DIKONFIRMASI','SELESAI') AND s.sale_date BETWEEN ? AND ?
                """, from, to), "v");
        BigDecimal hpp = Db.dec(db.queryOne("""
                SELECT COALESCE(SUM(si.hpp),0) AS v FROM sale_items si JOIN sales s ON s.id=si.sale_id
                WHERE s.status IN ('DIKONFIRMASI','SELESAI') AND s.sale_date BETWEEN ? AND ?
                """, from, to), "v");
        BigDecimal expenses = expensesTotal(from, to, branchId);
        BigDecimal feed = feedUsageTotal(from, to, branchId);
        BigDecimal losses = lossesTotal(from, to, branchId);
        BigDecimal gross = revenue.subtract(hpp);
        BigDecimal result = gross.subtract(expenses).subtract(feed).subtract(losses);
        out.put("from", from);
        out.put("to", to);
        out.put("revenue", revenue);
        out.put("hpp", hpp);
        out.put("grossMargin", Money.nz(gross));
        out.put("expenses", expenses);
        out.put("feedUsage", feed);
        out.put("losses", losses);
        out.put("periodResult", Money.nz(result));
        out.put("marginPercent", Money.percentage(result, revenue));
        out.put("reconciliation", List.of(
                Map.of("label", "Penjualan bersih", "amount", revenue),
                Map.of("label", "Harga pokok kambing terjual", "amount", hpp.negate()),
                Map.of("label", "Beban operasional/penjualan", "amount", expenses.negate()),
                Map.of("label", "Beban pakan (pemakaian)", "amount", feed.negate()),
                Map.of("label", "Kerugian persediaan", "amount", losses.negate()),
                Map.of("label", "Laba rugi periode", "amount", Money.nz(result))));
        out.put("note", "Laba manajerial per kambing adalah sepanjang pemeliharaan; laba rugi periode memakai beban periode. "
                + "Keduanya dapat berbeda karena beda waktu biaya.");
        return out;
    }

    public Map<String, Object> inventory(LocalDate asOf, Long branchId) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("goatCount", db.count("""
                SELECT COUNT(*) FROM goats WHERE status='AKTIF' AND entry_date <= ?
                  AND (? IS NULL OR branch_id = ?)
                """, asOf, branchId, branchId));
        out.put("goatValue", Db.dec(db.queryOne("""
                SELECT COALESCE(SUM(acquisition_cost),0) AS v FROM goats
                WHERE status='AKTIF' AND entry_date <= ? AND (? IS NULL OR branch_id = ?)
                """, asOf, branchId, branchId), "v"));
        out.put("feedValue", Db.dec(db.queryOne("""
                SELECT COALESCE(SUM(stock_qty * avg_cost),0) AS v FROM feed_items
                WHERE (? IS NULL OR branch_id = ?)
                """, branchId, branchId), "v"));
        out.put("goats", db.query("""
                SELECT g.code, p.name AS supplier_name, pen.name AS pen_name, g.health_status, g.sale_readiness,
                       g.acquisition_cost,
                       (SELECT w.weight_kg FROM weight_records w WHERE w.goat_id=g.id
                        ORDER BY w.measured_at DESC, w.id DESC LIMIT 1) AS latest_weight
                FROM goats g JOIN parties p ON p.id=g.supplier_id
                LEFT JOIN pen_placements pp ON pp.goat_id=g.id AND pp.exited_at IS NULL
                LEFT JOIN pens pen ON pen.id=pp.pen_id
                WHERE g.status='AKTIF' AND g.entry_date <= ? AND (? IS NULL OR g.branch_id = ?)
                ORDER BY g.code
                """, asOf, branchId, branchId));
        out.put("feed", db.query("""
                SELECT code, name, unit, stock_qty, avg_cost, stock_qty*avg_cost AS value FROM feed_items
                WHERE (? IS NULL OR branch_id = ?) ORDER BY code
                """, branchId, branchId));
        out.put("meatQty", Db.dec(db.queryOne("""
                SELECT COALESCE(SUM(qty_remaining_kg),0) AS v FROM meat_batches
                WHERE received_date <= ? AND (? IS NULL OR branch_id = ?)
                """, asOf, branchId, branchId), "v"));
        out.put("meatValue", Db.dec(db.queryOne("""
                SELECT COALESCE(SUM(qty_remaining_kg * hpp_per_kg),0) AS v FROM meat_batches
                WHERE received_date <= ? AND (? IS NULL OR branch_id = ?)
                """, asOf, branchId, branchId), "v"));
        out.put("meat", db.query("""
                SELECT mb.batch_no, g.code AS goat_code, mb.received_date, mb.qty_in_kg,
                       mb.qty_remaining_kg, mb.hpp_per_kg,
                       mb.qty_remaining_kg * mb.hpp_per_kg AS value, mb.status
                FROM meat_batches mb JOIN goats g ON g.id = mb.goat_id
                WHERE mb.received_date <= ? AND (? IS NULL OR mb.branch_id = ?)
                ORDER BY mb.received_date, mb.id
                """, asOf, branchId, branchId));
        out.put("asOf", asOf);
        return out;
    }

    public List<Map<String, Object>> operationalRecap(LocalDate from, LocalDate to, Long branchId) {
        return db.query("""
                SELECT * FROM (
                  SELECT p.purchase_date AS entry_date, 'PEMBELIAN' AS module, p.doc_no,
                         pt.name AS party_name, CONCAT('Pembelian kambing status ', p.status) AS description,
                         p.total_amount AS amount
                  FROM purchases p JOIN parties pt ON pt.id = p.supplier_id
                  WHERE p.purchase_date BETWEEN ? AND ? AND (? IS NULL OR p.branch_id = ?)
                  UNION ALL
                  SELECT s.sale_date, 'PENJUALAN', s.doc_no, pt.name,
                         CONCAT('Penjualan kambing status ', s.status), s.total_net
                  FROM sales s JOIN parties pt ON pt.id = s.customer_id
                  WHERE s.sale_date BETWEEN ? AND ? AND (? IS NULL OR s.branch_id = ?)
                  UNION ALL
                  SELECT e.expense_date, 'PENGELUARAN', e.doc_no, pt.name,
                         COALESCE(e.purpose, ec.name), e.amount
                  FROM expenses e JOIN expense_categories ec ON ec.id = e.category_id
                  LEFT JOIN parties pt ON pt.id = e.party_id
                  WHERE e.expense_date BETWEEN ? AND ? AND e.status = 'POSTED'
                    AND (? IS NULL OR e.branch_id = ?)
                  UNION ALL
                  SELECT r.receipt_date, 'PENERIMAAN', r.doc_no, pt.name,
                         CONCAT('Penerimaan ', (SELECT COUNT(*) FROM receipt_items ri WHERE ri.receipt_id = r.id), ' kambing'),
                         r.total_cost
                  FROM receipts r JOIN purchases p ON p.id = r.purchase_id
                  JOIN parties pt ON pt.id = p.supplier_id
                  WHERE r.receipt_date BETWEEN ? AND ? AND (? IS NULL OR r.branch_id = ?)
                  UNION ALL
                  SELECT so.slaughter_date, 'PEMOTONGAN', so.doc_no, NULL,
                         CONCAT('Pemotongan ', g.code, ' - daging ',
                                so.meat_weight_kg, ' kg / karkas ', so.carcass_weight_kg, ' kg'),
                         so.total_cost
                  FROM slaughter_orders so JOIN goats g ON g.id = so.goat_id
                  WHERE so.slaughter_date BETWEEN ? AND ? AND so.status = 'POSTED'
                    AND (? IS NULL OR so.branch_id = ?)
                ) x ORDER BY entry_date DESC, doc_no DESC
                """, from, to, branchId, branchId, from, to, branchId, branchId,
                from, to, branchId, branchId, from, to, branchId, branchId,
                from, to, branchId, branchId);
    }

    public Map<String, Object> analytics(Long branchId) {
        Map<String, Object> out = new LinkedHashMap<>();
        List<Map<String, Object>> monthly = new ArrayList<>();
        LocalDate now = LocalDate.now().withDayOfMonth(1);
        for (int i = 11; i >= 0; i--) {
            LocalDate start = now.minusMonths(i);
            LocalDate end = start.withDayOfMonth(start.lengthOfMonth());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("month", start.toString().substring(0, 7));
            m.put("revenue", Db.dec(db.queryOne("""
                    SELECT COALESCE(SUM(si.net),0) AS v FROM sale_items si JOIN sales s ON s.id=si.sale_id
                    WHERE s.status IN ('DIKONFIRMASI','SELESAI') AND s.sale_date BETWEEN ? AND ?
                      AND (? IS NULL OR s.branch_id = ?)
                    """, start, end, branchId, branchId), "v"));
            m.put("hpp", Db.dec(db.queryOne("""
                    SELECT COALESCE(SUM(si.hpp),0) AS v FROM sale_items si JOIN sales s ON s.id=si.sale_id
                    WHERE s.status IN ('DIKONFIRMASI','SELESAI') AND s.sale_date BETWEEN ? AND ?
                      AND (? IS NULL OR s.branch_id = ?)
                    """, start, end, branchId, branchId), "v"));
            m.put("expenses", expensesTotal(start, end, branchId));
            m.put("feedUsage", feedUsageTotal(start, end, branchId));
            m.put("losses", lossesTotal(start, end, branchId));
            m.put("salesCount", db.count("""
                    SELECT COUNT(*) FROM sale_items si JOIN sales s ON s.id=si.sale_id
                    WHERE s.status IN ('DIKONFIRMASI','SELESAI') AND s.sale_date BETWEEN ? AND ?
                      AND (? IS NULL OR s.branch_id = ?)
                    """, start, end, branchId, branchId));
            BigDecimal profit = Db.dec(m, "revenue").subtract(Db.dec(m, "hpp"))
                    .subtract(Db.dec(m, "expenses")).subtract(Db.dec(m, "feedUsage")).subtract(Db.dec(m, "losses"));
            m.put("profit", Money.nz(profit));
            m.put("cashIn", Db.dec(db.queryOne("""
                    SELECT COALESCE(SUM(amount),0) AS v FROM payments WHERE status='POSTED' AND direction='IN'
                    AND payment_date BETWEEN ? AND ? AND (? IS NULL OR branch_id = ?)
                    """, start, end, branchId, branchId), "v"));
            m.put("cashOut", Db.dec(db.queryOne("""
                    SELECT COALESCE(SUM(amount),0) AS v FROM payments WHERE status='POSTED' AND direction='OUT'
                    AND payment_date BETWEEN ? AND ? AND (? IS NULL OR branch_id = ?)
                    """, start, end, branchId, branchId), "v"));
            monthly.add(m);
        }
        out.put("monthly", monthly);
        out.put("suppliers", db.query("""
                SELECT pt.name AS supplier_name, COUNT(DISTINCT y.goat_id) AS sold_count,
                       COALESCE(AVG(COALESCE(snap.profit, y.revenue - y.hpp)),0) AS avg_profit,
                       COALESCE(AVG(DATEDIFF(y.sale_date, g.entry_date) + 1),0) AS avg_holding_days,
                       MIN(y.sale_date) AS first_sale, MAX(y.sale_date) AS last_sale
                FROM (
                  SELECT goat_id, SUM(net) AS revenue, SUM(hpp) AS hpp, MAX(sale_date) AS sale_date
                  FROM v_goat_sale_items GROUP BY goat_id
                ) y
                JOIN goats g ON g.id=y.goat_id JOIN parties pt ON pt.id=g.supplier_id
                LEFT JOIN goat_profit_snapshots snap ON snap.id=(
                  SELECT gps.id FROM goat_profit_snapshots gps WHERE gps.goat_id=g.id ORDER BY gps.version DESC LIMIT 1)
                WHERE (? IS NULL OR g.branch_id = ?)
                GROUP BY pt.id, pt.name ORDER BY sold_count DESC
                """, branchId, branchId));
        out.put("goatMargins", db.query("""
                SELECT g.code, COALESCE(snap.profit, y.revenue - y.hpp) AS profit, snap.margin_percent,
                       DATEDIFF(y.sale_date, g.entry_date) + 1 AS holding_days,
                       g.entry_weight_kg,
                       (SELECT w.weight_kg FROM weight_records w WHERE w.goat_id=g.id
                        ORDER BY w.measured_at DESC, w.id DESC LIMIT 1) AS last_weight
                FROM (
                  SELECT goat_id, SUM(net) AS revenue, SUM(hpp) AS hpp, MAX(sale_date) AS sale_date
                  FROM v_goat_sale_items GROUP BY goat_id
                ) y
                JOIN goats g ON g.id=y.goat_id
                LEFT JOIN goat_profit_snapshots snap ON snap.id=(
                  SELECT gps.id FROM goat_profit_snapshots gps WHERE gps.goat_id=g.id ORDER BY gps.version DESC LIMIT 1)
                WHERE (? IS NULL OR g.branch_id = ?)
                ORDER BY y.sale_date DESC LIMIT 100
                """, branchId, branchId));
        out.put("definitions", Map.of(
                "profit", "Laba manajerial per kambing = penjualan bersih - HPP - beban langsung - beban bersama teralokasi",
                "margin", "Laba manajerial / penjualan bersih x 100%; tidak tersedia bila penjualan nol",
                "holdingDays", "Tanggal masuk sampai tanggal serah terima, inklusif"));
        return out;
    }
}
