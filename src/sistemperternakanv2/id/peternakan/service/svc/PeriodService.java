package id.peternakan.service.svc;

import id.peternakan.domain.Permissions;
import id.peternakan.service.api.ApiException;
import id.peternakan.service.db.Db;
import id.peternakan.service.repo.PeriodRepository;
import id.peternakan.service.security.Audit;
import id.peternakan.service.security.SessionInfo;

import java.time.LocalDate;
import java.util.Map;

public final class PeriodService {
    private final Db db;
    private final PeriodRepository repo;

    public PeriodService(Db db, PeriodRepository repo) {
        this.db = db;
        this.repo = repo;
    }

    public Map<String, Object> list(SessionInfo session) {
        session.require(Permissions.PERIOD_CLOSE);
        return Map.of("items", repo.list(), "current", LocalDate.now().toString().substring(0, 7));
    }

    public Map<String, Object> close(SessionInfo session, Map<String, Object> body) {
        session.require(Permissions.PERIOD_CLOSE);
        if (!session.isOwner()) {
            throw ApiException.forbidden("Tutup periode hanya oleh owner.");
        }
        String code = PartyService.str(body, "periodCode");
        if (code == null || !code.matches("\\d{4}-\\d{2}")) {
            throw ApiException.badRequest("Kode periode harus berformat YYYY-MM.");
        }
        db.withTx(tx -> {
            repo.close(tx, code, session.userId());
            Audit.log(tx, session, "PERIODS", "CLOSE", "accounting_periods", code, null,
                    Map.of("status", "CLOSED"), PartyService.str(body, "reason"));
            return null;
        });
        return Map.of("periodCode", code, "status", "CLOSED");
    }

    public Map<String, Object> reopen(SessionInfo session, Map<String, Object> body) {
        session.require(Permissions.PERIOD_CLOSE);
        if (!session.isOwner()) {
            throw ApiException.forbidden("Buka periode hanya oleh owner.");
        }
        String code = PartyService.str(body, "periodCode");
        String reason = PartyService.str(body, "reason");
        db.withTx(tx -> {
            repo.reopen(tx, code, session.userId(), reason);
            Audit.log(tx, session, "PERIODS", "REOPEN", "accounting_periods", code, null,
                    Map.of("status", "OPEN"), reason);
            return null;
        });
        return Map.of("periodCode", code, "status", "OPEN");
    }
}
