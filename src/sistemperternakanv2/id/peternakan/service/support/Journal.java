package id.peternakan.service.support;

import id.peternakan.domain.Money;
import id.peternakan.service.api.ApiException;
import id.peternakan.service.db.Db;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Jurnal berpasangan otomatis. Debit harus sama dengan kredit per dokumen.
 */
public final class Journal {

    public record Line(String accountCode, BigDecimal debit, BigDecimal credit, String description) {
        public static Line debit(String code, BigDecimal amount, String description) {
            return new Line(code, Money.nz(amount), Money.ZERO, description);
        }

        public static Line credit(String code, BigDecimal amount, String description) {
            return new Line(code, Money.ZERO, Money.nz(amount), description);
        }
    }

    private Journal() {
    }

    public static long post(Db.Tx tx, LocalDate date, long branchId, String sourceType, long sourceId,
                            String eventKey, String description, long userId, List<Line> lines) {
        PeriodGuard.requireOpen(tx, date);
        if (lines == null || lines.isEmpty()) {
            throw ApiException.badRequest("Jurnal tidak memiliki baris.");
        }
        BigDecimal debit = Money.ZERO;
        BigDecimal credit = Money.ZERO;
        for (Line l : lines) {
            debit = debit.add(Money.nz(l.debit()));
            credit = credit.add(Money.nz(l.credit()));
        }
        if (debit.compareTo(credit) != 0) {
            throw new ApiException(500, "Jurnal tidak seimbang: debit " + debit + " kredit " + credit);
        }
        if (debit.signum() == 0) {
            return 0;
        }
        String docNo = DocNo.next(tx, "JR", date);
        long entryId = tx.insert("""
                INSERT INTO journal_entries (doc_no, branch_id, entry_date, source_type, source_id, event_key,
                description, created_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, docNo, branchId, date, sourceType, sourceId, eventKey, description, userId);
        for (Line l : lines) {
            long accountId = accountId(tx, l.accountCode());
            tx.update("""
                    INSERT INTO journal_lines (entry_id, account_id, debit, credit, description)
                    VALUES (?, ?, ?, ?, ?)
                    """, entryId, accountId, Money.nz(l.debit()), Money.nz(l.credit()), l.description());
        }
        return entryId;
    }

    public static long reverse(Db.Tx tx, long originalEntryId, LocalDate date, long userId, String reason) {
        Map<String, Object> entry = tx.queryOne("SELECT * FROM journal_entries WHERE id = ? FOR UPDATE", originalEntryId);
        if (entry == null) {
            throw ApiException.notFound("Jurnal asal tidak ditemukan.");
        }
        if ("REVERSED".equals(Db.str(entry, "status"))) {
            throw ApiException.conflict("Jurnal sudah dibalik.");
        }
        List<Map<String, Object>> lines = tx.query("""
                SELECT a.code, jl.debit, jl.credit, jl.description
                FROM journal_lines jl JOIN accounts a ON a.id = jl.account_id
                WHERE jl.entry_id = ?
                """, originalEntryId);
        List<Line> reversed = new ArrayList<>();
        for (Map<String, Object> l : lines) {
            reversed.add(new Line(Db.str(l, "code"), Db.dec(l, "credit"), Db.dec(l, "debit"),
                    "Pembalikan: " + Db.str(l, "description")));
        }
        String docNo = DocNo.next(tx, "JR", date);
        long reversalId = tx.insert("""
                INSERT INTO journal_entries (doc_no, branch_id, entry_date, source_type, source_id, event_key,
                description, status, reversal_of_id, created_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'POSTED', ?, ?)
                """, docNo, Db.lng(entry, "branch_id"), date, Db.str(entry, "source_type"),
                Db.lng(entry, "source_id"), Db.str(entry, "event_key") + ":REV",
                "Pembalikan " + Db.str(entry, "doc_no") + " - " + reason, originalEntryId, userId);
        for (Line l : reversed) {
            long accountId = accountId(tx, l.accountCode());
            tx.update("""
                    INSERT INTO journal_lines (entry_id, account_id, debit, credit, description)
                    VALUES (?, ?, ?, ?, ?)
                    """, reversalId, accountId, Money.nz(l.debit()), Money.nz(l.credit()), l.description());
        }
        tx.update("UPDATE journal_entries SET status = 'REVERSED' WHERE id = ?", originalEntryId);
        return reversalId;
    }

    public static long accountId(Db.Tx tx, String code) {
        Map<String, Object> account = tx.queryOne("SELECT id FROM accounts WHERE code = ?", code);
        if (account == null) {
            throw new ApiException(500, "Akun jurnal tidak ditemukan: " + code);
        }
        return Db.lng(account, "id");
    }

    public static void assertBalanced(Db db) {
        Map<String, Object> row = db.queryOne("""
                SELECT COUNT(*) AS n FROM (
                  SELECT e.id, SUM(l.debit) d, SUM(l.credit) c
                  FROM journal_entries e JOIN journal_lines l ON l.entry_id = e.id
                  GROUP BY e.id HAVING d <> c
                ) x
                """);
        if (row != null && ((Number) row.get("n")).longValue() > 0) {
            throw new ApiException(500, "Terdapat jurnal tidak seimbang.");
        }
    }
}
