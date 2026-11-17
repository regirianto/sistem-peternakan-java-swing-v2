package id.peternakan.service.svc;

import id.peternakan.domain.Permissions;
import id.peternakan.service.db.Db;
import id.peternakan.service.repo.PenRepository;
import id.peternakan.service.security.Audit;
import id.peternakan.service.security.SessionInfo;
import id.peternakan.service.support.BranchAccess;

import java.util.Map;

public final class PenService {
    private final Db db;
    private final PenRepository repo;

    public PenService(Db db, PenRepository repo) {
        this.db = db;
        this.repo = repo;
    }

    public Map<String, Object> list(SessionInfo session, Long branchId) {
        session.require(Permissions.GOAT_READ);
        Long filter = BranchAccess.filter(db, session, branchId);
        return Map.of("items", repo.list(filter));
    }

    public Map<String, Object> find(SessionInfo session, long id) {
        session.require(Permissions.GOAT_READ);
        Map<String, Object> pen = repo.find(id);
        BranchAccess.requireBranch(db, session, Db.lng(pen, "branch_id"));
        pen.put("goats", db.query("""
                SELECT g.id, g.code, g.gender, g.jenis, g.health_status, g.sale_readiness,
                       g.entry_date, pp.entered_at,
                       COALESCE((SELECT w.weight_kg FROM weight_records w WHERE w.goat_id = g.id
                         ORDER BY w.measured_at DESC, w.id DESC LIMIT 1), g.entry_weight_kg) AS latest_weight
                FROM pen_placements pp JOIN goats g ON g.id=pp.goat_id
                WHERE pp.pen_id=? AND pp.exited_at IS NULL ORDER BY g.code
                """, id));
        return pen;
    }

    public Map<String, Object> create(SessionInfo session, Map<String, Object> body) {
        session.require(Permissions.PEN_MANAGE);
        long branchId = BranchAccess.requireWrite(db, session, PartyService.longVal(body, "branchId"));
        long id = db.withTx(tx -> {
            long newId = repo.create(tx, branchId, PartyService.str(body, "code"),
                    PartyService.str(body, "name"), intVal(body, "capacity", 0), PartyService.str(body, "notes"));
            Audit.log(tx, session, "PENS", "CREATE", "pens", String.valueOf(newId), null, body, null);
            return newId;
        });
        return Map.of("id", id);
    }

    public Map<String, Object> update(SessionInfo session, long id, Map<String, Object> body) {
        session.require(Permissions.PEN_MANAGE);
        Map<String, Object> before = repo.find(id);
        BranchAccess.requireBranch(db, session, Db.lng(before, "branch_id"));
        db.withTx(tx -> {
            repo.update(tx, id, PartyService.str(body, "name"), intVal(body, "capacity", 0),
                    PartyService.str(body, "status") == null ? "AKTIF" : PartyService.str(body, "status"),
                    PartyService.str(body, "notes"));
            Audit.log(tx, session, "PENS", "UPDATE", "pens", String.valueOf(id), before, body, null);
            return null;
        });
        return Map.of("id", id, "updated", true);
    }

    private int intVal(Map<String, Object> body, String key, int fallback) {
        Object v = body.get(key);
        return v == null ? fallback : Integer.parseInt(String.valueOf(v));
    }
}
