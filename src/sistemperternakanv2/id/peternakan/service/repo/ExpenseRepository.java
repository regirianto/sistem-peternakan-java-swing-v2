package id.peternakan.service.repo;

import id.peternakan.domain.Money;
import id.peternakan.service.api.ApiException;
import id.peternakan.service.db.Db;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ExpenseRepository {
    private final Db db;

    public ExpenseRepository(Db db) {
        this.db = db;
    }

    public List<Map<String, Object>> categories() {
        return db.query("SELECT * FROM expense_categories ORDER BY code");
    }

    public long createCategory(Db.Tx tx, String code, String name, String treatment, boolean allocatable) {
        if (code == null || code.isBlank() || name == null || name.isBlank()) {
            throw ApiException.badRequest("Kode dan nama kategori wajib diisi.");
        }
        if (!List.of("OPERASIONAL", "PENJUALAN", "ASET", "PEROLEHAN").contains(treatment)) {
            throw ApiException.badRequest("Perlakuan kategori tidak dikenal.");
        }
        return tx.insert("""
                INSERT INTO expense_categories (code, name, treatment, allocatable) VALUES (?, ?, ?, ?)
                """, code.trim(), name.trim(), treatment, allocatable ? 1 : 0);
    }

    public List<Map<String, Object>> list(String q, String status, String from, String to, int page, int size,
                                          Long branchId) {
        StringBuilder sql = new StringBuilder("""
                SELECT e.id, e.doc_no, e.expense_date, e.amount, e.status, e.allocatable, e.service_start,
                       e.service_end, ec.name AS category_name, ec.treatment, pt.name AS party_name,
                       g.code AS goat_code, pen.name AS pen_name, b.name AS branch_name
                FROM expenses e JOIN expense_categories ec ON ec.id = e.category_id
                JOIN branches b ON b.id = e.branch_id
                LEFT JOIN parties pt ON pt.id = e.party_id
                LEFT JOIN goats g ON g.id = e.direct_goat_id
                LEFT JOIN pens pen ON pen.id = e.scope_pen_id
                WHERE 1=1
                """);
        List<Object> args = new ArrayList<>();
        if (q != null && !q.isBlank()) {
            sql.append(" AND (e.doc_no LIKE ? OR ec.name LIKE ? OR e.purpose LIKE ?)");
            String like = "%" + q.trim() + "%";
            args.add(like);
            args.add(like);
            args.add(like);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND e.status = ?");
            args.add(status);
        }
        if (from != null && !from.isBlank()) {
            sql.append(" AND e.expense_date >= ?");
            args.add(LocalDate.parse(from));
        }
        if (to != null && !to.isBlank()) {
            sql.append(" AND e.expense_date <= ?");
            args.add(LocalDate.parse(to));
        }
        if (branchId != null) {
            sql.append(" AND e.branch_id = ?");
            args.add(branchId);
        }
        sql.append(" ORDER BY e.expense_date DESC, e.id DESC LIMIT ? OFFSET ?");
        args.add(size);
        args.add((page - 1) * size);
        return db.query(sql.toString(), args.toArray());
    }

    public long count(String q, String status, String from, String to, Long branchId) {
        StringBuilder sql = new StringBuilder("""
                SELECT COUNT(*) AS n FROM expenses e JOIN expense_categories ec ON ec.id = e.category_id WHERE 1=1
                """);
        List<Object> args = new ArrayList<>();
        if (q != null && !q.isBlank()) {
            sql.append(" AND (e.doc_no LIKE ? OR ec.name LIKE ? OR e.purpose LIKE ?)");
            String like = "%" + q.trim() + "%";
            args.add(like);
            args.add(like);
            args.add(like);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND e.status = ?");
            args.add(status);
        }
        if (from != null && !from.isBlank()) {
            sql.append(" AND e.expense_date >= ?");
            args.add(LocalDate.parse(from));
        }
        if (to != null && !to.isBlank()) {
            sql.append(" AND e.expense_date <= ?");
            args.add(LocalDate.parse(to));
        }
        if (branchId != null) {
            sql.append(" AND e.branch_id = ?");
            args.add(branchId);
        }
        return db.count(sql.toString(), args.toArray());
    }

    public Map<String, Object> find(long id) {
        Map<String, Object> expense = db.queryOne("""
                SELECT e.*, ec.name AS category_name, ec.treatment, pt.name AS party_name,
                       g.code AS goat_code, pen.name AS pen_name, b.name AS branch_name
                FROM expenses e JOIN expense_categories ec ON ec.id = e.category_id
                JOIN branches b ON b.id = e.branch_id
                LEFT JOIN parties pt ON pt.id = e.party_id
                LEFT JOIN goats g ON g.id = e.direct_goat_id
                LEFT JOIN pens pen ON pen.id = e.scope_pen_id
                WHERE e.id = ?
                """, id);
        if (expense == null) {
            throw ApiException.notFound("Pengeluaran tidak ditemukan.");
        }
        expense.put("cost_source", db.queryOne("SELECT * FROM cost_sources WHERE source_type = 'EXPENSE' AND source_id = ?", id));
        expense.put("batches", db.query("""
                SELECT ab.* FROM allocation_batches ab JOIN cost_sources cs ON cs.id = ab.cost_source_id
                WHERE cs.source_type = 'EXPENSE' AND cs.source_id = ? ORDER BY ab.version DESC
                """, id));
        return expense;
    }

    public long create(Db.Tx tx, String docNo, long branchId, long categoryId, LocalDate date, BigDecimal amount,
                       Long partyId, String purpose, String notes, boolean allocatable, LocalDate serviceStart,
                       LocalDate serviceEnd, Long scopePenId, Long directGoatId,
                       long userId) {
        if (amount == null || amount.signum() <= 0) {
            throw ApiException.badRequest("Nilai pengeluaran harus lebih dari 0.");
        }
        if (directGoatId != null) {
            allocatable = false;
        }
        return tx.insert("""
                INSERT INTO expenses (doc_no, branch_id, category_id, expense_date, amount, party_id, purpose, notes,
                status, service_start, service_end, scope_pen_id, allocatable, direct_goat_id,
                created_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'DRAFT', ?, ?, ?, ?, ?, ?)
                """, docNo, branchId, categoryId, date, Money.nz(amount), partyId, purpose, notes,
            serviceStart, serviceEnd, scopePenId, allocatable ? 1 : 0, directGoatId, userId);
    }

    public Map<String, Object> lock(Db.Tx tx, long id) {
        Map<String, Object> e = tx.queryOne("SELECT * FROM expenses WHERE id = ? FOR UPDATE", id);
        if (e == null) {
            throw ApiException.notFound("Pengeluaran tidak ditemukan.");
        }
        return e;
    }

    public void setStatus(Db.Tx tx, long id, String status) {
        tx.update("UPDATE expenses SET status = ?, posted_at = NOW() WHERE id = ?", status, id);
    }

    public String accountCodeForTreatment(String treatment) {
        return switch (treatment) {
            case "PENJUALAN" -> "6103";
            case "ASET" -> "1302";
            case "PEROLEHAN" -> "1301";
            default -> "6102";
        };
    }

    public List<Map<String, Object>> postable(Long branchId) {
        return db.query("""
                SELECT id, doc_no, expense_date, amount FROM expenses
                WHERE status = 'DRAFT' AND (? IS NULL OR branch_id = ?) ORDER BY expense_date
                """, branchId, branchId);
    }

    public BigDecimal periodTotal(String from, String to) {
        return Db.dec(db.queryOne("""
                SELECT COALESCE(SUM(e.amount),0) AS v FROM expenses e
                JOIN expense_categories ec ON ec.id = e.category_id
                WHERE e.status = 'POSTED' AND e.expense_date BETWEEN ? AND ?
                  AND ec.treatment IN ('OPERASIONAL','PENJUALAN')
                """, LocalDate.parse(from), LocalDate.parse(to)), "v");
    }
}
