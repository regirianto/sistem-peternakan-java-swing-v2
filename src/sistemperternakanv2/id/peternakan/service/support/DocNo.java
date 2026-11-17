package id.peternakan.service.support;

import id.peternakan.service.api.ApiException;
import id.peternakan.service.db.Db;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

public final class DocNo {
    private static final DateTimeFormatter PERIOD = DateTimeFormatter.ofPattern("yyyyMM");

    private DocNo() {
    }

    public static String next(Db.Tx tx, String prefix, LocalDate date) {
        String period = date.format(PERIOD);
        tx.update("""
                INSERT INTO doc_sequences (prefix, period, last_no) VALUES (?, ?, 1)
                ON DUPLICATE KEY UPDATE last_no = last_no + 1
                """, prefix, period);
        Map<String, Object> row = tx.queryOne("SELECT last_no FROM doc_sequences WHERE prefix = ? AND period = ?",
                prefix, period);
        int no = row == null ? 1 : ((Number) row.get("last_no")).intValue();
        return "%s-%s-%06d".formatted(prefix, period, no);
    }

    public static void requireDate(LocalDate date, String field) {
        if (date == null) {
            throw ApiException.badRequest(field + " wajib diisi.");
        }
    }
}
