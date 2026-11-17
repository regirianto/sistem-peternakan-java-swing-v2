package id.peternakan.service.svc;

import id.peternakan.domain.Money;
import id.peternakan.domain.Permissions;
import id.peternakan.domain.ProfitCalculator;
import id.peternakan.service.db.Db;
import id.peternakan.service.repo.ProfitRepository;
import id.peternakan.service.security.Audit;
import id.peternakan.service.security.SessionInfo;
import id.peternakan.service.support.BranchAccess;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ProfitService {
    private final Db db;
    private final ProfitRepository repo;

    public ProfitService(Db db, ProfitRepository repo) {
        this.db = db;
        this.repo = repo;
    }

    public static Map<String, Object> componentsToMap(ProfitCalculator.Components c) {
        ProfitCalculator.Result r = ProfitCalculator.calculate(c);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("revenue", r.revenue());
        out.put("hpp", r.hpp());
        out.put("directCost", r.directCost());
        out.put("allocatedCost", r.allocatedCost());
        out.put("grossMargin", r.grossMargin());
        out.put("profit", r.profit());
        out.put("marginPercent", r.marginPercent());
        return out;
    }

    public Map<String, Object> live(SessionInfo session, long goatId) {
        session.requireFinance();
        requireGoatBranch(session, goatId);
        return db.withTx(tx -> componentsToMap(repo.components(tx, goatId)));
    }

    public Map<String, Object> list(SessionInfo session, String from, String to, String q, int page, int size,
                                    Long branchId) {
        session.requireFinance();
        Long filter = BranchAccess.filter(db, session, branchId);
        return Map.of("items", repo.list(from, to, q, page, size, filter),
                "total", repo.count(from, to, q, filter));
    }

    public Map<String, Object> recap(SessionInfo session, String from, String to, Long branchId) {
        session.requireFinance();
        Long filter = BranchAccess.filter(db, session, branchId);
        return repo.recap(from, to, filter);
    }

    public Map<String, Object> finalize(SessionInfo session, long goatId) {
        session.require(Permissions.PERIOD_CLOSE);
        requireGoatBranch(session, goatId);
        repo.finalize(goatId, session.userId());
        db.withTx(tx -> {
            Audit.log(tx, session, "PROFIT", "FINALIZE", "goats", String.valueOf(goatId), null,
                    Map.of("status", "FINAL"), "Finalisasi laba per kambing");
            return null;
        });
        return Map.of("goatId", goatId, "status", "FINAL");
    }

    public Map<String, Object> snapshots(SessionInfo session, long goatId) {
        session.requireFinance();
        requireGoatBranch(session, goatId);
        return Map.of("items", repo.snapshots(goatId), "latest", repo.latestSnapshot(goatId));
    }

    private void requireGoatBranch(SessionInfo session, long goatId) {
        var goat = db.queryOne("SELECT branch_id FROM goats WHERE id = ?", goatId);
        if (goat == null) {
            throw id.peternakan.service.api.ApiException.notFound("Kambing tidak ditemukan.");
        }
        BranchAccess.requireBranch(db, session, Db.lng(goat, "branch_id"));
    }

    public static BigDecimal nz(BigDecimal value) {
        return Money.nz(value);
    }
}
