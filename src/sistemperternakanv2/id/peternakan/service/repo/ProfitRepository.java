package id.peternakan.service.repo;

import id.peternakan.domain.Money;
import id.peternakan.domain.ProfitCalculator;
import id.peternakan.service.api.ApiException;
import id.peternakan.service.db.Db;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ProfitRepository {
    private final Db db;

    public ProfitRepository(Db db) {
        this.db = db;
    }

    public ProfitCalculator.Components components(Db.Tx tx, long goatId) {
        Map<String, Object> sale = tx.queryOne("""
                SELECT COALESCE(SUM(net),0) AS net, COALESCE(SUM(hpp),0) AS hpp
                FROM v_goat_sale_items WHERE goat_id = ?
                """, goatId);
        BigDecimal revenue = Db.dec(sale, "net");
        BigDecimal hpp = Db.dec(sale, "hpp");
        hpp = hpp.add(Db.dec(tx.queryOne("""
                SELECT COALESCE(SUM(mm.total_cost),0) AS v FROM meat_movements mm
                JOIN meat_batches mb ON mb.id = mm.batch_id
                WHERE mb.goat_id = ? AND mm.movement_type = 'SUSUT'
                """, goatId), "v"));
        BigDecimal direct = Db.dec(tx.queryOne("""
                SELECT COALESCE(SUM(amount),0) AS v FROM expenses
                WHERE direct_goat_id = ? AND status = 'POSTED'
                """, goatId), "v");
        direct = direct.add(Db.dec(tx.queryOne("""
                SELECT COALESCE(SUM(amount),0) AS v FROM cost_sources
                WHERE direct_goat_id = ? AND source_type = 'FEED_USAGE'
                """, goatId), "v"));
        BigDecimal allocated = Db.dec(tx.queryOne("""
                SELECT COALESCE(SUM(ca.amount),0) AS v FROM cost_allocations ca
                JOIN allocation_batches ab ON ab.id = ca.batch_id AND ab.status = 'POSTED'
                WHERE ca.goat_id = ?
                """, goatId), "v");
        return new ProfitCalculator.Components(revenue, hpp, direct, allocated);
    }

    public Map<String, Object> createSnapshot(Db.Tx tx, long goatId, String status, long userId) {
        ProfitCalculator.Components c = components(tx, goatId);
        ProfitCalculator.Result r = ProfitCalculator.calculate(c);
        Map<String, Object> sale = tx.queryOne("""
                SELECT sale_item_id AS id FROM v_goat_sale_items WHERE goat_id = ?
                ORDER BY sale_date DESC, sale_item_id DESC LIMIT 1
                """, goatId);
        Map<String, Object> last = tx.queryOne("SELECT COALESCE(MAX(version),0) AS v FROM goat_profit_snapshots WHERE goat_id = ?", goatId);
        int version = (last == null ? 0 : ((Number) last.get("v")).intValue()) + 1;
        long id = tx.insert("""
                INSERT INTO goat_profit_snapshots (goat_id, sale_item_id, version, status, revenue, hpp,
                direct_cost, allocated_cost, profit, margin_percent, finalized_at, finalized_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, goatId, sale == null ? null : Db.lng(sale, "id"), version, status,
                r.revenue(), r.hpp(), r.directCost(), r.allocatedCost(), r.profit(), r.marginPercent(),
                "FINAL".equals(status) ? java.time.LocalDateTime.now() : null,
                "FINAL".equals(status) ? userId : null);
        return Map.of("id", id, "version", version, "status", status, "profit", r.profit(),
                "revenue", r.revenue(), "hpp", r.hpp(), "directCost", r.directCost(),
                "allocatedCost", r.allocatedCost(), "marginPercent", r.marginPercent());
    }

    public void refreshAffected(Db.Tx tx, List<Long> goatIds, long userId) {
        for (long goatId : goatIds) {
            Map<String, Object> sold = tx.queryOne("""
                    SELECT 1 AS x FROM v_goat_sale_items WHERE goat_id = ? LIMIT 1
                    """, goatId);
            if (sold != null) {
                createSnapshot(tx, goatId, "SEMENTARA", userId);
            }
        }
    }

    public List<Map<String, Object>> list(String from, String to, String q, int page, int size,
                                          Long branchId) {
        StringBuilder sql = new StringBuilder("""
                SELECT x.goat_id, g.code, g.entry_date, x.sale_date, pt.name AS supplier_name,
                       DATEDIFF(x.sale_date, g.entry_date) + 1 AS holding_days,
                       COALESCE(snap.revenue, x.revenue) AS revenue,
                       COALESCE(snap.hpp, x.hpp) AS hpp,
                       COALESCE(snap.direct_cost,0) AS direct_cost,
                       COALESCE(snap.allocated_cost,0) AS allocated_cost,
                       COALESCE(snap.profit, x.revenue - x.hpp) AS profit,
                       snap.margin_percent, COALESCE(snap.status,'SEMENTARA') AS snapshot_status,
                       snap.version, x.sale_doc_no
                FROM (
                  SELECT v.goat_id, MAX(v.sale_date) AS sale_date, SUM(v.net) AS revenue, SUM(v.hpp) AS hpp,
                         SUBSTRING_INDEX(GROUP_CONCAT(v.sale_doc_no ORDER BY v.sale_date DESC,
                           v.sale_item_id DESC), ',', 1) AS sale_doc_no
                  FROM v_goat_sale_items v GROUP BY v.goat_id
                ) x
                JOIN goats g ON g.id = x.goat_id
                JOIN parties pt ON pt.id = g.supplier_id
                LEFT JOIN goat_profit_snapshots snap ON snap.id = (
                    SELECT gps.id FROM goat_profit_snapshots gps WHERE gps.goat_id = g.id
                    ORDER BY gps.version DESC LIMIT 1)
                WHERE 1=1
                """);
        List<Object> args = new ArrayList<>();
        if (from != null && !from.isBlank()) {
            sql.append(" AND x.sale_date >= ?");
            args.add(LocalDate.parse(from));
        }
        if (to != null && !to.isBlank()) {
            sql.append(" AND x.sale_date <= ?");
            args.add(LocalDate.parse(to));
        }
        if (q != null && !q.isBlank()) {
            sql.append(" AND g.code LIKE ?");
            args.add("%" + q.trim() + "%");
        }
        if (branchId != null) {
            sql.append(" AND g.branch_id = ?");
            args.add(branchId);
        }
        sql.append(" ORDER BY x.sale_date DESC, g.code LIMIT ? OFFSET ?");
        args.add(size);
        args.add((page - 1) * size);
        return db.query(sql.toString(), args.toArray());
    }

    public long count(String from, String to, String q, Long branchId) {
        StringBuilder sql = new StringBuilder("""
                SELECT COUNT(DISTINCT v.goat_id) AS n FROM v_goat_sale_items v
                JOIN goats g ON g.id = v.goat_id WHERE 1=1
                """);
        List<Object> args = new ArrayList<>();
        if (from != null && !from.isBlank()) {
            sql.append(" AND v.sale_date >= ?");
            args.add(LocalDate.parse(from));
        }
        if (to != null && !to.isBlank()) {
            sql.append(" AND v.sale_date <= ?");
            args.add(LocalDate.parse(to));
        }
        if (q != null && !q.isBlank()) {
            sql.append(" AND g.code LIKE ?");
            args.add("%" + q.trim() + "%");
        }
        if (branchId != null) {
            sql.append(" AND g.branch_id = ?");
            args.add(branchId);
        }
        return db.count(sql.toString(), args.toArray());
    }

    public Map<String, Object> latestSnapshot(long goatId) {
        return db.queryOne("""
                SELECT gps.*, u.full_name AS finalized_by_name FROM goat_profit_snapshots gps
                LEFT JOIN users u ON u.id = gps.finalized_by
                WHERE gps.goat_id = ? ORDER BY gps.version DESC LIMIT 1
                """, goatId);
    }

    public List<Map<String, Object>> snapshots(long goatId) {
        return db.query("""
                SELECT gps.*, u.full_name AS finalized_by_name FROM goat_profit_snapshots gps
                LEFT JOIN users u ON u.id = gps.finalized_by
                WHERE gps.goat_id = ? ORDER BY gps.version DESC
                """, goatId);
    }

    public Map<String, Object> recap(String from, String to, Long branchId) {
        Map<String, Object> row = db.queryOne("""
                SELECT COUNT(*) AS sold_count,
                       SUM(CASE WHEN x.profit > 0 THEN 1 ELSE 0 END) AS profit_count,
                       SUM(CASE WHEN x.profit < 0 THEN 1 ELSE 0 END) AS loss_count,
                       COALESCE(SUM(x.profit),0) AS total_profit,
                       COALESCE(AVG(x.profit),0) AS avg_profit,
                       COALESCE(SUM(x.revenue),0) AS total_revenue,
                       COALESCE(SUM(x.hpp),0) AS total_hpp,
                       COALESCE(SUM(x.direct_cost + x.allocated_cost),0) AS total_cost
                FROM (
                  SELECT COALESCE(snap.profit, y.revenue - y.hpp) AS profit,
                         COALESCE(snap.revenue, y.revenue) AS revenue,
                         COALESCE(snap.hpp, y.hpp) AS hpp,
                         COALESCE(snap.direct_cost,0) AS direct_cost,
                         COALESCE(snap.allocated_cost,0) AS allocated_cost
                  FROM (
                    SELECT v.goat_id, SUM(v.net) AS revenue, SUM(v.hpp) AS hpp, MAX(v.sale_date) AS sale_date
                    FROM v_goat_sale_items v GROUP BY v.goat_id
                  ) y
                  JOIN goats g ON g.id = y.goat_id
                  LEFT JOIN goat_profit_snapshots snap ON snap.id = (
                    SELECT gps.id FROM goat_profit_snapshots gps WHERE gps.goat_id = g.id
                    ORDER BY gps.version DESC LIMIT 1)
                  WHERE y.sale_date BETWEEN ? AND ? AND (? IS NULL OR g.branch_id = ?)
                ) x
                """, LocalDate.parse(from), LocalDate.parse(to), branchId, branchId);
        return row == null ? Map.of() : row;
    }

    public void finalize(long goatId, long userId) {
        db.withTx(tx -> {
            Map<String, Object> sold = tx.queryOne("""
                    SELECT 1 AS x FROM v_goat_sale_items WHERE goat_id = ? LIMIT 1
                    """, goatId);
            if (sold == null) {
                throw ApiException.conflict("Kambing belum memiliki penjualan efektif.");
            }
            createSnapshot(tx, goatId, "FINAL", userId);
            return null;
        });
    }
}
