package id.peternakan.service.svc;

import id.peternakan.domain.Permissions;
import id.peternakan.service.db.Db;
import id.peternakan.service.repo.JournalRepository;
import id.peternakan.service.security.SessionInfo;
import id.peternakan.service.support.BranchAccess;

import java.util.Map;

public final class JournalService {
    private final Db db;
    private final JournalRepository repo;

    public JournalService(Db db, JournalRepository repo) {
        this.db = db;
        this.repo = repo;
    }

    public Map<String, Object> list(SessionInfo session, String from, String to, String sourceType, String q,
                                    int page, int size, Long branchId) {
        session.require(Permissions.FINANCE_VIEW);
        Long filter = BranchAccess.filter(db, session, branchId);
        return Map.of("items", repo.list(from, to, sourceType, q, page, size, filter),
                "total", repo.count(from, to, sourceType, q, filter));
    }

    public Map<String, Object> find(SessionInfo session, long id) {
        session.require(Permissions.FINANCE_VIEW);
        Map<String, Object> entry = repo.find(id);
        BranchAccess.requireBranch(db, session, Db.lng(entry, "branch_id"));
        return entry;
    }

    public Map<String, Object> bySource(SessionInfo session, String sourceType, long sourceId) {
        session.require(Permissions.FINANCE_VIEW);
        return Map.of("items", repo.bySource(sourceType, sourceId));
    }

    public Map<String, Object> reconciliation(SessionInfo session, String from, String to, Long branchId) {
        session.require(Permissions.FINANCE_VIEW);
        Long filter = BranchAccess.filter(db, session, branchId);
        return repo.reconciliation(from, to, filter);
    }
}
