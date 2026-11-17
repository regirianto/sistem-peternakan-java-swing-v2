package id.peternakan.service.repo;

import id.peternakan.domain.Money;
import id.peternakan.service.api.ApiException;
import id.peternakan.service.db.Db;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class JournalRepository {
    private final Db db;

    public JournalRepository(Db db) {
        this.db = db;
    }

    public List<Map<String, Object>> list(String from, String to, String sourceType, String q, int page, int size,
                                          Long branchId) {
        StringBuilder sql = new StringBuilder("""
                SELECT je.id, je.doc_no, je.entry_date, je.source_type, je.source_id, je.description, je.status,
                       je.reversal_of_id, u.full_name AS created_by_name, b.name AS branch_name,
                       (SELECT COALESCE(SUM(jl.debit),0) FROM journal_lines jl WHERE jl.entry_id=je.id) AS total
                FROM journal_entries je LEFT JOIN users u ON u.id=je.created_by
                JOIN branches b ON b.id = je.branch_id WHERE 1=1
                """);
        List<Object> args = new ArrayList<>();
        if (from != null && !from.isBlank()) {
            sql.append(" AND je.entry_date >= ?");
            args.add(LocalDate.parse(from));
        }
        if (to != null && !to.isBlank()) {
            sql.append(" AND je.entry_date <= ?");
            args.add(LocalDate.parse(to));
        }
        if (sourceType != null && !sourceType.isBlank()) {
            sql.append(" AND je.source_type = ?");
            args.add(sourceType);
        }
        if (q != null && !q.isBlank()) {
            sql.append(" AND (je.doc_no LIKE ? OR je.description LIKE ?)");
            String like = "%" + q.trim() + "%";
            args.add(like);
            args.add(like);
        }
        if (branchId != null) {
            sql.append(" AND je.branch_id = ?");
            args.add(branchId);
        }
        sql.append(" ORDER BY je.entry_date DESC, je.id DESC LIMIT ? OFFSET ?");
        args.add(size);
        args.add((page - 1) * size);
        return db.query(sql.toString(), args.toArray());
    }

    public long count(String from, String to, String sourceType, String q, Long branchId) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) AS n FROM journal_entries je WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (from != null && !from.isBlank()) {
            sql.append(" AND je.entry_date >= ?");
            args.add(LocalDate.parse(from));
        }
        if (to != null && !to.isBlank()) {
            sql.append(" AND je.entry_date <= ?");
            args.add(LocalDate.parse(to));
        }
        if (sourceType != null && !sourceType.isBlank()) {
            sql.append(" AND je.source_type = ?");
            args.add(sourceType);
        }
        if (q != null && !q.isBlank()) {
            sql.append(" AND (je.doc_no LIKE ? OR je.description LIKE ?)");
            String like = "%" + q.trim() + "%";
            args.add(like);
            args.add(like);
        }
        if (branchId != null) {
            sql.append(" AND je.branch_id = ?");
            args.add(branchId);
        }
        return db.count(sql.toString(), args.toArray());
    }

    public Map<String, Object> find(long id) {
        Map<String, Object> entry = db.queryOne("SELECT * FROM journal_entries WHERE id = ?", id);
        if (entry == null) {
            throw ApiException.notFound("Jurnal tidak ditemukan.");
        }
        entry.put("lines", db.query("""
                SELECT jl.*, a.code AS account_code, a.name AS account_name FROM journal_lines jl
                JOIN accounts a ON a.id=jl.account_id WHERE jl.entry_id=? ORDER BY jl.id
                """, id));
        return entry;
    }

    public List<Map<String, Object>> bySource(String sourceType, long sourceId) {
        return db.query("""
                SELECT * FROM journal_entries WHERE source_type=? AND source_id=? ORDER BY id
                """, sourceType, sourceId);
    }

    public List<Map<String, Object>> trialBalance(String from, String to, Long branchId) {
        LocalDate start = from == null || from.isBlank() ? LocalDate.now().withDayOfMonth(1) : LocalDate.parse(from);
        LocalDate end = to == null || to.isBlank() ? LocalDate.now() : LocalDate.parse(to);
        return db.query("""
                SELECT a.code, a.name, a.account_type,
                       COALESCE(SUM(jl.debit),0) AS debit, COALESCE(SUM(jl.credit),0) AS credit,
                       COALESCE(SUM(jl.debit - jl.credit),0) AS balance
                FROM journal_lines jl JOIN journal_entries je ON je.id=jl.entry_id
                JOIN accounts a ON a.id=jl.account_id
                WHERE je.entry_date BETWEEN ? AND ? AND (? IS NULL OR je.branch_id = ?)
                GROUP BY a.id, a.code, a.name, a.account_type
                HAVING debit <> 0 OR credit <> 0 ORDER BY a.code
                """, start, end, branchId, branchId);
    }

    public Map<String, Object> reconciliation(String from, String to, Long branchId) {
        LocalDate today = LocalDate.now();
        LocalDate start = from == null || from.isBlank() ? today.withDayOfMonth(1) : LocalDate.parse(from);
        LocalDate end = to == null || to.isBlank() ? today : LocalDate.parse(to);
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("trialBalance", trialBalance(start.toString(), end.toString(), branchId));
        BigDecimal debit = Db.dec(db.queryOne("""
                SELECT COALESCE(SUM(jl.debit),0) AS v FROM journal_lines jl
                JOIN journal_entries je ON je.id=jl.entry_id
                WHERE je.entry_date BETWEEN ? AND ? AND (? IS NULL OR je.branch_id = ?)
                """, start, end, branchId, branchId), "v");
        BigDecimal credit = Db.dec(db.queryOne("""
                SELECT COALESCE(SUM(jl.credit),0) AS v FROM journal_lines jl
                JOIN journal_entries je ON je.id=jl.entry_id
                WHERE je.entry_date BETWEEN ? AND ? AND (? IS NULL OR je.branch_id = ?)
                """, start, end, branchId, branchId), "v");
        out.put("totalDebit", debit);
        out.put("totalCredit", credit);
        out.put("balanced", debit.compareTo(credit) == 0);
        out.put("unbalancedEntries", db.query("""
                SELECT je.doc_no, je.entry_date, SUM(jl.debit) d, SUM(jl.credit) c
                FROM journal_entries je JOIN journal_lines jl ON jl.entry_id=je.id
                WHERE je.entry_date BETWEEN ? AND ? AND (? IS NULL OR je.branch_id = ?)
                GROUP BY je.id, je.doc_no, je.entry_date HAVING d <> c
                """, start, end, branchId, branchId));
        return out;
    }
}
