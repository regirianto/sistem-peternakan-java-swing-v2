package id.peternakan.service.svc;

import id.peternakan.domain.Money;
import id.peternakan.domain.Permissions;
import id.peternakan.service.api.ApiException;
import id.peternakan.service.db.Db;
import id.peternakan.service.repo.AllocationRepository;
import id.peternakan.service.repo.FeedRepository;
import id.peternakan.service.security.Audit;
import id.peternakan.service.security.SessionInfo;
import id.peternakan.service.support.BranchAccess;
import id.peternakan.service.support.DocNo;
import id.peternakan.service.support.Idempotency;
import id.peternakan.service.support.Journal;
import id.peternakan.service.support.PeriodGuard;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class FeedService {
    private final Db db;
    private final FeedRepository repo;
    private final AllocationRepository allocations;

    public FeedService(Db db, FeedRepository repo, AllocationRepository allocations) {
        this.db = db;
        this.repo = repo;
        this.allocations = allocations;
    }

    public Map<String, Object> items(SessionInfo session, Long branchId) {
        session.require(Permissions.FEED_RECORD);
        Long filter = BranchAccess.filter(db, session, branchId);
        return Map.of("items", repo.items(filter));
    }

    public Map<String, Object> createItem(SessionInfo session, Map<String, Object> body) {
        session.require(Permissions.FEED_MANAGE);
        long branchId = BranchAccess.requireWrite(db, session, PartyService.longVal(body, "branchId"));
        long id = db.withTx(tx -> {
            long newId = repo.createItem(tx, branchId, PartyService.str(body, "code"),
                    PartyService.str(body, "name"), PartyService.str(body, "unit"),
                    Money.of(String.valueOf(body.getOrDefault("minStock", "0"))));
            Audit.log(tx, session, "FEED", "CREATE_ITEM", "feed_items", String.valueOf(newId), null, body, null);
            return newId;
        });
        return Map.of("id", id);
    }

    public Map<String, Object> receive(SessionInfo session, Map<String, Object> body, String requestId) {
        session.require(Permissions.FEED_MANAGE);
        long itemId = Long.parseLong(String.valueOf(body.get("feedItemId")));
        LocalDate date = LocalDate.parse(String.valueOf(body.get("movementDate")));
        BigDecimal qty = Money.of(String.valueOf(body.get("qty")));
        BigDecimal unitCost = new BigDecimal(String.valueOf(body.get("unitCost")));
        return db.withTx(tx -> Idempotency.run(tx, requestId, session.userId(), "FEED_RECEIVE", () -> {
            PeriodGuard.requireOpen(tx, date);
            Map<String, Object> item = repo.findItem(itemId);
            BranchAccess.requireBranch(db, session, Db.lng(item, "branch_id"));
            Map<String, Object> result = repo.receive(tx, itemId, date, qty, unitCost,
                    PartyService.str(body, "notes"), session.userId());
            BigDecimal total = (BigDecimal) result.get("total");
            String cashCode = "1101";
            Journal.post(tx, date, Db.lng(item, "branch_id"), "FEED_RECEIVE", itemId,
                    "FEED_IN:" + result.get("movementId"), "Penerimaan pakan", session.userId(), List.of(
                            Journal.Line.debit("1302", total, "Persediaan pakan"),
                            Journal.Line.credit(cashCode, total, "Kas/bank keluar")));
            Audit.log(tx, session, "FEED", "RECEIVE", "feed_items", String.valueOf(itemId), null,
                    Map.of("qty", qty, "unitCost", unitCost, "total", total), null);
            return Map.of("movementId", result.get("movementId"), "total", total, "newAvg", result.get("newAvg"));
        }));
    }

    public Map<String, Object> usage(SessionInfo session, Map<String, Object> body, String requestId) {
        session.require(Permissions.FEED_RECORD);
        long itemId = Long.parseLong(String.valueOf(body.get("feedItemId")));
        LocalDate date = LocalDate.parse(String.valueOf(body.get("movementDate")));
        BigDecimal qty = Money.of(String.valueOf(body.get("qty")));
        Long penId = body.get("penId") == null ? null : Long.parseLong(String.valueOf(body.get("penId")));
        Long goatId = body.get("goatId") == null ? null : Long.parseLong(String.valueOf(body.get("goatId")));
        LocalDate serviceStart = body.get("serviceStart") == null ? null
                : LocalDate.parse(String.valueOf(body.get("serviceStart")));
        LocalDate serviceEnd = body.get("serviceEnd") == null ? null
                : LocalDate.parse(String.valueOf(body.get("serviceEnd")));
        if (penId == null && goatId == null) {
            throw ApiException.badRequest("Pemakaian pakan harus merujuk kandang atau kambing.");
        }
        return db.withTx(tx -> Idempotency.run(tx, requestId, session.userId(), "FEED_USAGE", () -> {
            PeriodGuard.requireOpen(tx, date);
            Map<String, Object> item = repo.findItem(itemId);
            long branchId = Db.lng(item, "branch_id");
            BranchAccess.requireBranch(db, session, branchId);
            if (penId != null) {
                Map<String, Object> pen = tx.queryOne("SELECT branch_id FROM pens WHERE id = ?", penId);
                if (pen == null || Db.lng(pen, "branch_id") != branchId) {
                    throw ApiException.badRequest("Kandang pemakaian pakan berada di cabang lain.");
                }
            }
            if (goatId != null) {
                Map<String, Object> goat = tx.queryOne("SELECT branch_id FROM goats WHERE id = ?", goatId);
                if (goat == null || Db.lng(goat, "branch_id") != branchId) {
                    throw ApiException.badRequest("Kambing pemakaian pakan berada di cabang lain.");
                }
            }
            Map<String, Object> result = repo.usage(tx, itemId, date, qty, penId, goatId, serviceStart, serviceEnd,
                    PartyService.str(body, "notes"), session.userId());
            BigDecimal total = (BigDecimal) result.get("total");
            Journal.post(tx, date, branchId, "FEED_USAGE", itemId, "FEED_OUT:" + result.get("movementId"),
                    "Pemakaian pakan", session.userId(), List.of(
                            Journal.Line.debit("6101", total, "Beban pakan"),
                            Journal.Line.credit("1302", total, "Persediaan pakan")));
            long movementId = ((Number) result.get("movementId")).longValue();
            LocalDate rangeStart = serviceStart == null ? date : serviceStart;
            LocalDate rangeEnd = serviceEnd == null ? date : serviceEnd;
            allocations.createCostSource(tx, "FEED_USAGE", movementId, total, rangeStart, rangeEnd,
                    penId, goatId, goatId == null);
            Map<String, Object> allocation = Map.of("posted", false);
            if (goatId == null) {
                try {
                    allocation = allocations.finalize(tx, Db.lng(tx.queryOne(
                            "SELECT id FROM cost_sources WHERE source_type='FEED_USAGE' AND source_id=?",
                            movementId), "id"), session.userId());
                    allocation = Map.of("posted", true, "batchId", allocation.get("batchId"),
                            "version", allocation.get("version"));
                } catch (ApiException e) {
                    // Tanpa kambing pada rentang: biaya tetap tercatat, alokasi menunggu tindakan owner.
                    allocation = Map.of("posted", false, "reason", e.getMessage());
                }
            }
            Audit.log(tx, session, "FEED", "USAGE", "feed_items", String.valueOf(itemId), null,
                    Map.of("qty", qty, "total", total, "penId", penId == null ? "" : penId,
                            "goatId", goatId == null ? "" : goatId), null);
            return Map.of("movementId", result.get("movementId"), "total", total,
                    "unitCost", result.get("unitCost"), "allocation", allocation);
        }));
    }

    public Map<String, Object> movements(SessionInfo session, Long itemId, Long branchId) {
        session.require(Permissions.FEED_RECORD);
        Long filter = BranchAccess.filter(db, session, branchId);
        return Map.of("items", repo.movements(itemId, 200, filter));
    }

    public Map<String, Object> stockValue(SessionInfo session, Long branchId) {
        session.requireFinance();
        Long filter = BranchAccess.filter(db, session, branchId);
        return Map.of("value", repo.stockValue(filter));
    }
}
