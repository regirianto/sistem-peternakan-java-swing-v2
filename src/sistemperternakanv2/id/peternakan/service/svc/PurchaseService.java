package id.peternakan.service.svc;

import id.peternakan.domain.Permissions;
import id.peternakan.service.api.ApiException;
import id.peternakan.service.db.Db;
import id.peternakan.service.repo.PurchaseRepository;
import id.peternakan.service.security.Audit;
import id.peternakan.service.security.SessionInfo;
import id.peternakan.service.support.BranchAccess;
import id.peternakan.service.support.DocNo;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class PurchaseService {
    private final Db db;
    private final PurchaseRepository repo;

    public PurchaseService(Db db, PurchaseRepository repo) {
        this.db = db;
        this.repo = repo;
    }

    public Map<String, Object> list(SessionInfo session, String q, String status, int page, int size,
                                    Long branchId) {
        session.require(Permissions.PURCHASE_MANAGE);
        Long filter = BranchAccess.filter(db, session, branchId);
        return Map.of("items", repo.list(q, status, page, size, filter),
                "total", repo.count(q, status, filter));
    }

    public Map<String, Object> find(SessionInfo session, long id) {
        session.require(Permissions.PURCHASE_MANAGE);
        Map<String, Object> purchase = repo.find(id);
        BranchAccess.requireBranch(db, session, Db.lng(purchase, "branch_id"));
        return purchase;
    }

    public Map<String, Object> open(SessionInfo session, Long branchId) {
        session.require(Permissions.RECEIPT_MANAGE);
        Long filter = BranchAccess.filter(db, session, branchId);
        return Map.of("items", repo.openForReceipt(filter));
    }

    public Map<String, Object> create(SessionInfo session, Map<String, Object> body) {
        session.require(Permissions.PURCHASE_MANAGE);
        long supplierId = Long.parseLong(String.valueOf(body.get("supplierId")));
        LocalDate date = LocalDate.parse(String.valueOf(body.get("purchaseDate")));
        LocalDate dueDate = PartyService.str(body, "dueDate") == null ? null : LocalDate.parse(PartyService.str(body, "dueDate"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
        if (items == null || items.isEmpty()) {
            throw ApiException.badRequest("Pembelian harus memiliki minimal satu item kambing.");
        }
        Map<String, Object> supplier = db.queryOne("""
                SELECT p.*, pr.role_code FROM parties p JOIN party_roles pr ON pr.party_id=p.id
                WHERE p.id=? AND pr.role_code='SUPPLIER'
                """, supplierId);
        if (supplier == null) {
            throw ApiException.badRequest("Supplier tidak ditemukan atau tidak memiliki peran SUPPLIER.");
        }
        if (!Boolean.TRUE.equals(supplier.get("active"))) {
            throw ApiException.conflict("Supplier tidak aktif.");
        }
        long branchId = BranchAccess.requireWrite(db, session, PartyService.longVal(body, "branchId"));
        Map<String, Object> result = db.withTx(tx -> {
            String docNo = DocNo.next(tx, "PB", date);
            long id = repo.createDraft(tx, docNo, branchId, supplierId, date, dueDate,
                    PartyService.str(body, "notes"), items, session.userId());
            Audit.log(tx, session, "PURCHASES", "CREATE", "purchases", String.valueOf(id), null,
                    Map.of("docNo", docNo, "supplierId", supplierId, "items", items), null);
            return Map.<String, Object>of("id", id, "docNo", docNo);
        });
        return result;
    }

    public Map<String, Object> confirm(SessionInfo session, long id) {
        session.require(Permissions.PURCHASE_CONFIRM);
        return db.withTx(tx -> {
            Map<String, Object> purchase = repo.lockPurchase(tx, id);
            BranchAccess.requireBranch(db, session, Db.lng(purchase, "branch_id"));
            if (!"DRAFT".equals(Db.str(purchase, "status"))) {
                throw ApiException.conflict("Hanya pembelian DRAFT yang dapat dikonfirmasi.");
            }
            repo.setStatus(tx, id, "DIKONFIRMASI");
            Audit.log(tx, session, "PURCHASES", "CONFIRM", "purchases", String.valueOf(id),
                    Map.of("status", "DRAFT"), Map.of("status", "DIKONFIRMASI"), null);
            return Map.<String, Object>of("id", id, "status", "DIKONFIRMASI");
        });
    }

    public Map<String, Object> cancel(SessionInfo session, long id, String reason) {
        session.require(Permissions.PURCHASE_MANAGE);
        return db.withTx(tx -> {
            Map<String, Object> purchase = repo.lockPurchase(tx, id);
            BranchAccess.requireBranch(db, session, Db.lng(purchase, "branch_id"));
            String status = Db.str(purchase, "status");
            if ("DIBATALKAN".equals(status)) {
                throw ApiException.conflict("Pembelian sudah dibatalkan.");
            }
            long receipts = Db.lng(tx.queryOne("SELECT COUNT(*) AS n FROM receipts WHERE purchase_id=?", id), "n");
            if (receipts > 0) {
                throw ApiException.conflict("Pembelian sudah memiliki penerimaan. Pembatalan penerimaan harus diselesaikan lebih dulu.");
            }
            if (!"DRAFT".equals(status) && !session.isOwner()) {
                throw ApiException.forbidden("Pembatalan pembelian terkonfirmasi hanya oleh owner.");
            }
            repo.setStatus(tx, id, "DIBATALKAN");
            Audit.log(tx, session, "PURCHASES", "CANCEL", "purchases", String.valueOf(id),
                    Map.of("status", status), Map.of("status", "DIBATALKAN"), reason);
            return Map.<String, Object>of("id", id, "status", "DIBATALKAN");
        });
    }

    public Map<String, Object> closeRemaining(SessionInfo session, long id, String reason) {
        if (!session.isOwner()) {
            throw ApiException.forbidden("Penutupan sisa pembelian hanya oleh owner.");
        }
        if (reason == null || reason.isBlank()) {
            throw ApiException.badRequest("Alasan penutupan sisa wajib diisi.");
        }
        return db.withTx(tx -> {
            Map<String, Object> purchase = repo.lockPurchase(tx, id);
            BranchAccess.requireBranch(db, session, Db.lng(purchase, "branch_id"));
            if (!List.of("DIKONFIRMASI", "DITERIMA_SEBAGIAN").contains(Db.str(purchase, "status"))) {
                throw ApiException.conflict("Status pembelian tidak dapat ditutup sisanya.");
            }
            repo.setStatus(tx, id, "SELESAI");
            Audit.log(tx, session, "PURCHASES", "CLOSE_REMAINING", "purchases", String.valueOf(id),
                    Map.of("status", Db.str(purchase, "status")), Map.of("status", "SELESAI"), reason);
            return Map.<String, Object>of("id", id, "status", "SELESAI");
        });
    }
}
