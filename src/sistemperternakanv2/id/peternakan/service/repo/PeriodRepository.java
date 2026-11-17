package id.peternakan.service.repo;

import id.peternakan.service.api.ApiException;
import id.peternakan.service.db.Db;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class PeriodRepository {
    private final Db db;

    public PeriodRepository(Db db) {
        this.db = db;
    }

    public List<Map<String, Object>> list() {
        return db.query("""
                SELECT ap.*, cu.full_name AS closed_by_name, ru.full_name AS reopened_by_name
                FROM accounting_periods ap
                LEFT JOIN users cu ON cu.id=ap.closed_by
                LEFT JOIN users ru ON ru.id=ap.reopened_by
                ORDER BY ap.period_code DESC
                """);
    }

    public Map<String, Object> ensure(String code) {
        String periodCode = code == null || code.isBlank() ? LocalDate.now().toString().substring(0, 7) : code;
        Map<String, Object> period = db.queryOne("SELECT * FROM accounting_periods WHERE period_code = ?", periodCode);
        if (period != null) {
            return period;
        }
        LocalDate start = LocalDate.parse(periodCode + "-01");
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());
        return Map.of("period_code", periodCode, "start_date", start, "end_date", end, "status", "OPEN");
    }

    public void close(Db.Tx tx, String code, long userId) {
        Map<String, Object> period = tx.queryOne("SELECT * FROM accounting_periods WHERE period_code = ? FOR UPDATE", code);
        LocalDate start;
        LocalDate end;
        if (period == null) {
            start = LocalDate.parse(code + "-01");
            end = start.withDayOfMonth(start.lengthOfMonth());
            tx.insert("""
                    INSERT INTO accounting_periods (period_code, start_date, end_date, status, closed_by, closed_at)
                    VALUES (?, ?, ?, 'CLOSED', ?, NOW())
                    """, code, start, end, userId);
        } else {
            if ("CLOSED".equals(Db.str(period, "status"))) {
                throw ApiException.conflict("Periode " + code + " sudah ditutup.");
            }
            tx.update("""
                    UPDATE accounting_periods SET status='CLOSED', closed_by=?, closed_at=NOW() WHERE period_code=?
                    """, userId, code);
        }
        long drafts = Db.lng(tx.queryOne("""
                SELECT (SELECT COUNT(*) FROM sales WHERE status='DRAFT' AND DATE_FORMAT(sale_date,'%Y-%m')=?) +
                       (SELECT COUNT(*) FROM purchases WHERE status='DRAFT' AND DATE_FORMAT(purchase_date,'%Y-%m')=?) +
                       (SELECT COUNT(*) FROM expenses WHERE status='DRAFT' AND DATE_FORMAT(expense_date,'%Y-%m')=?) AS n
                """, code, code, code), "n");
        if (drafts > 0) {
            // Draft tetap boleh ada, tetapi ditandai pada respons audit.
        }
    }

    public void reopen(Db.Tx tx, String code, long userId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw ApiException.badRequest("Alasan pembukaan kembali periode wajib diisi.");
        }
        Map<String, Object> period = tx.queryOne("SELECT * FROM accounting_periods WHERE period_code = ? FOR UPDATE", code);
        if (period == null) {
            throw ApiException.notFound("Periode tidak ditemukan.");
        }
        if (!"CLOSED".equals(Db.str(period, "status"))) {
            throw ApiException.conflict("Periode belum ditutup.");
        }
        tx.update("""
                UPDATE accounting_periods SET status='OPEN', reopened_by=?, reopened_at=NOW(), reopen_reason=?
                WHERE period_code=?
                """, userId, reason, code);
    }

    public List<Map<String, Object>> closedBetween(LocalDate from, LocalDate to) {
        return db.query("""
                SELECT * FROM accounting_periods WHERE status='CLOSED' AND start_date <= ? AND end_date >= ?
                """, to, from);
    }
}
