package id.peternakan.service.svc;

import id.peternakan.domain.Permissions;
import id.peternakan.service.db.Db;
import id.peternakan.service.repo.AllocationRepository;
import id.peternakan.service.security.Audit;
import id.peternakan.service.security.SessionInfo;
import id.peternakan.service.support.BranchAccess;

import java.util.Map;

public final class AllocationService {
    private final Db db;
    private final AllocationRepository repo;

    public AllocationService(Db db, AllocationRepository repo) {
        this.db = db;
        this.repo = repo;
    }

    public Map<String, Object> batches(SessionInfo session, long costSourceId) {
        session.requireFinance();
        return Map.of("source", repo.costSource(costSourceId), "items", repo.batches(costSourceId));
    }

    public Map<String, Object> allocations(SessionInfo session, long batchId) {
        session.requireFinance();
        return Map.of("items", repo.allocations(batchId));
    }

    public Map<String, Object> preview(SessionInfo session, long costSourceId) {
        session.require(Permissions.ALLOCATION_FINALIZE);
        return db.withTx(tx -> repo.preview(tx, costSourceId));
    }

    public Map<String, Object> finalize(SessionInfo session, long costSourceId) {
        session.require(Permissions.ALLOCATION_FINALIZE);
        return db.withTx(tx -> {
            Map<String, Object> result = repo.finalize(tx, costSourceId, session.userId());
            Audit.log(tx, session, "ALLOCATIONS", "FINALIZE", "cost_sources", String.valueOf(costSourceId),
                    null, result, "Finalisasi alokasi biaya bersama");
            return result;
        });
    }

    public Map<String, Object> pending(SessionInfo session, Long branchId) {
        session.require(Permissions.ALLOCATION_FINALIZE);
        Long filter = BranchAccess.filter(db, session, branchId);
        return Map.of("items", repo.pendingSources(filter));
    }

    public Map<String, Object> costSourceByDocument(SessionInfo session, String type, long sourceId) {
        session.requireFinance();
        Map<String, Object> source = repo.findCostSource(type, sourceId);
        if (source == null) {
            return Map.of("found", false);
        }
        long id = ((Number) source.get("id")).longValue();
        return Map.of("found", true, "source", source, "batches", repo.batches(id));
    }
}
