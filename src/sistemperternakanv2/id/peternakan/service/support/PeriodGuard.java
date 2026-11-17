package id.peternakan.service.support;

import id.peternakan.service.api.ApiException;
import id.peternakan.service.db.Db;

import java.time.LocalDate;

public final class PeriodGuard {
    private PeriodGuard() {
    }

    public static void requireOpen(Db.Tx tx, LocalDate date) {
        if (date == null) {
            throw ApiException.badRequest("Tanggal transaksi wajib diisi.");
        }
        Long closed = tx.query("""
                SELECT id FROM accounting_periods
                WHERE status = 'CLOSED' AND ? BETWEEN start_date AND end_date
                """, date).stream().findFirst().map(r -> ((Number) r.get("id")).longValue()).orElse(null);
        if (closed != null) {
            throw ApiException.conflict("Periode untuk tanggal " + date + " sudah ditutup. Transaksi mundur ditolak.");
        }
    }
}
