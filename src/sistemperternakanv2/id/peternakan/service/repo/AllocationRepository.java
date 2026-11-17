package id.peternakan.service.repo;

import id.peternakan.domain.AllocationCalculator;
import id.peternakan.domain.Money;
import id.peternakan.service.api.ApiException;
import id.peternakan.service.db.Db;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class AllocationRepository {
    private final Db db;
    private final GoatRepository goats;
    private final ProfitRepository profits;

    public AllocationRepository(Db db, GoatRepository goats, ProfitRepository profits) {
        this.db = db;
        this.goats = goats;
        this.profits = profits;
    }

    public Map<String, Object> costSource(long id) {
        Map<String, Object> source = db.queryOne("SELECT * FROM cost_sources WHERE id = ?", id);
        if (source == null) {
            throw ApiException.notFound("Sumber biaya tidak ditemukan.");
        }
        return source;
    }

    public List<Map<String, Object>> batches(long costSourceId) {
        return db.query("""
                SELECT ab.*, (SELECT COUNT(*) FROM cost_allocations ca WHERE ca.batch_id = ab.id) AS goat_count
                FROM allocation_batches ab WHERE ab.cost_source_id = ? ORDER BY ab.version DESC
                """, costSourceId);
    }

    public List<Map<String, Object>> allocations(long batchId) {
        return db.query("""
                SELECT ca.*, g.code AS goat_code FROM cost_allocations ca JOIN goats g ON g.id = ca.goat_id
                WHERE ca.batch_id = ? ORDER BY g.code
                """, batchId);
    }

    public Map<String, Object> preview(Db.Tx tx, long costSourceId) {
        Map<String, Object> source = tx.queryOne("SELECT * FROM cost_sources WHERE id = ?", costSourceId);
        if (source == null) {
            throw ApiException.notFound("Sumber biaya tidak ditemukan.");
        }
        BigDecimal amount = Db.dec(source, "amount");
        LocalDate start = Db.date(source, "service_start");
        LocalDate end = Db.date(source, "service_end");
        if (start == null || end == null) {
            throw ApiException.conflict("Rentang layanan biaya belum diisi.");
        }
        Long directGoat = source.get("direct_goat_id") == null ? null : ((Number) source.get("direct_goat_id")).longValue();
        if (directGoat != null) {
            Map<String, Object> goat = tx.queryOne("SELECT id, code FROM goats WHERE id = ?", directGoat);
            Map<String, Object> result = new java.util.LinkedHashMap<>();
            result.put("source", source);
            result.put("total", amount);
            result.put("items", List.of(Map.of("goatId", directGoat, "goatCode", Db.str(goat, "code"),
                    "days", 1, "amount", amount)));
            return result;
        }
        Long penScope = source.get("scope_pen_id") == null ? null : ((Number) source.get("scope_pen_id")).longValue();
        List<Map<String, Object>> candidates = tx.query("""
                SELECT id, code FROM goats WHERE entry_date <= ?
                AND (death_date IS NULL OR death_date >= ?)
                ORDER BY code
                """, end, start);
        List<AllocationCalculator.WeightedGoat> weights = new ArrayList<>();
        for (Map<String, Object> g : candidates) {
            long days = goats.presenceDays(tx, Db.lng(g, "id"), start, end, penScope);
            if (days > 0) {
                weights.add(new AllocationCalculator.WeightedGoat(Db.lng(g, "id"), Db.str(g, "code"), days));
            }
        }
        if (weights.isEmpty()) {
            throw ApiException.conflict("Penyebut alokasi nol: tidak ada hari-keberadaan pada rentang biaya. "
                    + "Biaya sumber tetap tercatat satu kali; periksa rentang atau lingkup.");
        }
        List<AllocationCalculator.Allocation> allocations = AllocationCalculator.allocate(amount, weights);
        List<Map<String, Object>> items = new ArrayList<>();
        for (AllocationCalculator.Allocation a : allocations) {
            items.add(Map.of("goatId", a.goatId(), "goatCode", a.goatCode(), "days", a.days(), "amount", a.amount()));
        }
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("source", source);
        result.put("total", amount);
        result.put("items", items);
        return result;
    }

    /** Finalisasi alokasi: versi baru POSTED, versi POSTED lama dibalik. */
    public Map<String, Object> finalize(Db.Tx tx, long costSourceId, long userId) {
        Map<String, Object> preview = preview(tx, costSourceId);
        Map<String, Object> source = tx.queryOne("SELECT * FROM cost_sources WHERE id = ? FOR UPDATE", costSourceId);
        Map<String, Object> last = tx.queryOne("""
                SELECT COALESCE(MAX(version),0) AS v FROM allocation_batches WHERE cost_source_id = ?
                """, costSourceId);
        int version = (last == null ? 0 : ((Number) last.get("v")).intValue()) + 1;
        tx.update("""
                UPDATE allocation_batches SET status = 'REVERSED'
                WHERE cost_source_id = ? AND status = 'POSTED'
                """, costSourceId);
        long batchId = tx.insert("""
                INSERT INTO allocation_batches (cost_source_id, version, method, status, period_start, period_end,
                total_amount, created_by, finalized_at) VALUES (?, ?, 'HARI_KEBERADAAN', 'POSTED', ?, ?, ?, ?, NOW())
                """, costSourceId, version, Db.date(source, "service_start"), Db.date(source, "service_end"),
                Db.dec(source, "amount"), userId);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) preview.get("items");
        List<Long> goatIds = new ArrayList<>();
        for (Map<String, Object> item : items) {
            tx.update("""
                    INSERT INTO cost_allocations (batch_id, goat_id, days, amount) VALUES (?, ?, ?, ?)
                    """, batchId, item.get("goatId"), ((Number) item.get("days")).longValue(), item.get("amount"));
            goatIds.add(((Number) item.get("goatId")).longValue());
        }
        profits.refreshAffected(tx, goatIds, userId);
        return Map.of("batchId", batchId, "version", version, "total", Db.dec(source, "amount"),
                "goatCount", items.size());
    }

    public void createCostSource(Db.Tx tx, String type, long sourceId, BigDecimal amount, LocalDate start,
                                 LocalDate end, Long penId, Long goatId, boolean allocatable) {
        tx.update("""
                INSERT INTO cost_sources (source_type, source_id, amount, service_start, service_end, scope_pen_id,
                direct_goat_id, allocatable) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE amount = VALUES(amount)
                """, type, sourceId, Money.nz(amount), start, end, penId, goatId, allocatable ? 1 : 0);
    }

    public Map<String, Object> findCostSource(String type, long sourceId) {
        return db.queryOne("SELECT * FROM cost_sources WHERE source_type = ? AND source_id = ?", type, sourceId);
    }

    public List<Map<String, Object>> pendingSources(Long branchId) {
        return db.query("""
                SELECT cs.*, (SELECT COUNT(*) FROM allocation_batches ab
                              WHERE ab.cost_source_id = cs.id AND ab.status = 'POSTED') AS posted_batches
                FROM cost_sources cs
                LEFT JOIN expenses e ON cs.source_type = 'EXPENSE' AND e.id = cs.source_id
                LEFT JOIN feed_movements fm ON cs.source_type = 'FEED_USAGE' AND fm.id = cs.source_id
                LEFT JOIN feed_items f ON f.id = fm.feed_item_id
                WHERE cs.allocatable = 1
                  AND (? IS NULL OR COALESCE(e.branch_id, f.branch_id) = ?)
                HAVING posted_batches = 0 ORDER BY cs.id DESC
                """, branchId, branchId);
    }
}
