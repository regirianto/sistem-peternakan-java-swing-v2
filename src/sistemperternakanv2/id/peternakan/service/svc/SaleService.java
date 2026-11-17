package id.peternakan.service.svc;

import id.peternakan.domain.Money;
import id.peternakan.domain.Permissions;
import id.peternakan.service.api.ApiException;
import id.peternakan.service.db.Db;
import id.peternakan.service.repo.ProfitRepository;
import id.peternakan.service.repo.SaleRepository;
import id.peternakan.service.security.Audit;
import id.peternakan.service.security.SessionInfo;
import id.peternakan.service.support.BranchAccess;
import id.peternakan.service.support.DocNo;
import id.peternakan.service.support.Idempotency;
import id.peternakan.service.support.Journal;
import id.peternakan.service.support.PeriodGuard;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class SaleService {
    private final Db db;
    private final SaleRepository sales;
    private final ProfitRepository profits;

    public SaleService(Db db, SaleRepository sales, ProfitRepository profits) {
        this.db = db;
        this.sales = sales;
        this.profits = profits;
    }

    public Map<String, Object> list(SessionInfo session, String q, String status, int page, int size,
                                    Long branchId) {
        session.require(Permissions.SALE_MANAGE);
        Long filter = BranchAccess.filter(db, session, branchId);
        return Map.of("items", sales.list(q, status, page, size, filter),
                "total", sales.count(q, status, filter));
    }

    public Map<String, Object> find(SessionInfo session, long id) {
        session.require(Permissions.SALE_MANAGE);
        Map<String, Object> sale = sales.find(id);
        BranchAccess.requireBranch(db, session, Db.lng(sale, "branch_id"));
        if (!session.isOwner()) {
            sale.remove("total_hpp");
            for (Map<String, Object> item : cast(sale.get("items"))) {
                item.remove("hpp");
                item.remove("acquisition_cost");
                item.remove("batch_hpp");
            }
        }
        return sale;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> saveDraft(SessionInfo session, Long saleId, Map<String, Object> body) {
        session.require(Permissions.SALE_MANAGE);
        long customerId = Long.parseLong(String.valueOf(body.get("customerId")));
        LocalDate date = LocalDate.parse(String.valueOf(body.get("saleDate")));
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
        Map<String, Object> customer = db.queryOne("""
                SELECT p.*, pr.role_code FROM parties p JOIN party_roles pr ON pr.party_id=p.id
                WHERE p.id=? AND pr.role_code='CUSTOMER'
                """, customerId);
        if (customer == null) {
            throw ApiException.badRequest("Pelanggan tidak ditemukan atau tidak memiliki peran CUSTOMER.");
        }
        if (!Boolean.TRUE.equals(customer.get("active"))) {
            throw ApiException.conflict("Pelanggan tidak aktif.");
        }
        long branchId;
        if (saleId == null) {
            branchId = BranchAccess.requireWrite(db, session, PartyService.longVal(body, "branchId"));
        } else {
            Map<String, Object> existing = sales.find(saleId);
            branchId = Db.lng(existing, "branch_id");
            BranchAccess.requireBranch(db, session, branchId);
        }
        long branch = branchId;
        return db.withTx(tx -> {
            String docNo = saleId == null ? DocNo.next(tx, "PJ", date) : null;
            long id = sales.saveDraft(tx, saleId, docNo, branch, customerId, date, PartyService.str(body, "notes"),
                    items, session.userId());
            Audit.log(tx, session, "SALES", saleId == null ? "CREATE_DRAFT" : "UPDATE_DRAFT", "sales",
                    String.valueOf(id), null, Map.of("customerId", customerId, "items", items.size()), null);
            Map<String, Object> result = new HashMap<>();
            result.put("id", id);
            if (docNo != null) {
                result.put("docNo", docNo);
            }
            return result;
        });
    }

    public Map<String, Object> confirm(SessionInfo session, long id, String requestId) {
        session.require(Permissions.SALE_CONFIRM);
        return db.withTx(tx -> Idempotency.run(tx, requestId, session.userId(), "SALE_CONFIRM", () -> {
            Map<String, Object> sale = sales.lockSale(tx, id);
            BranchAccess.requireBranch(db, session, Db.lng(sale, "branch_id"));
            if (!"DRAFT".equals(Db.str(sale, "status"))) {
                throw ApiException.conflict("Hanya penjualan DRAFT yang dapat dikonfirmasi.");
            }
            LocalDate date = Db.date(sale, "sale_date");
            PeriodGuard.requireOpen(tx, date);
            String saleType = Db.str(sale, "sale_type");
            boolean meatSale = "DAGING".equals(saleType);
            List<Map<String, Object>> items = sales.lockItems(tx, id);
            if (items.isEmpty()) {
                throw ApiException.conflict("Penjualan tidak memiliki item.");
            }
            BigDecimal totalNet = Money.ZERO;
            BigDecimal totalHpp = Money.ZERO;
            boolean belowCost = false;
            java.util.LinkedHashSet<Long> profitGoats = new java.util.LinkedHashSet<>();
            for (Map<String, Object> item : items) {
                BigDecimal hpp;
                if ("DAGING".equals(Db.str(item, "item_type"))) {
                    BigDecimal qty = Db.dec(item, "weight_kg");
                    if (qty.signum() <= 0) {
                        throw ApiException.conflict("Jumlah kilogram daging tidak valid.");
                    }
                    if (!"OPEN".equals(Db.str(item, "batch_status"))) {
                        throw ApiException.conflict("Batch daging " + Db.str(item, "batch_no") + " sudah habis.");
                    }
                    BigDecimal remaining = Db.dec(item, "qty_remaining_kg");
                    if (remaining.compareTo(qty) < 0) {
                        throw ApiException.conflict("Sisa batch " + Db.str(item, "batch_no") + " hanya "
                                + remaining.stripTrailingZeros().toPlainString() + " kg.");
                    }
                    hpp = Money.multiply(qty, Db.dec(item, "batch_hpp"));
                    profitGoats.add(Db.lng(item, "meat_goat_id"));
                } else {
                    if (!"AKTIF".equals(Db.str(item, "status"))) {
                        throw ApiException.conflict("Kambing " + Db.str(item, "code") + " tidak lagi aktif.");
                    }
                    if (!"SIAP_JUAL".equals(Db.str(item, "sale_readiness"))) {
                        throw ApiException.conflict("Kambing " + Db.str(item, "code") + " tidak layak jual.");
                    }
                    if (tx.queryOne("SELECT 1 AS x FROM goat_sale_locks WHERE goat_id=? AND sale_id<>?",
                            Db.lng(item, "goat_id"), id) != null) {
                        throw ApiException.conflict("Kambing " + Db.str(item, "code") + " sudah terjual pada dokumen lain.");
                    }
                    hpp = Db.dec(item, "acquisition_cost");
                    profitGoats.add(Db.lng(item, "goat_id"));
                }
                tx.update("UPDATE sale_items SET hpp=? WHERE id=?", hpp, Db.lng(item, "id"));
                totalNet = totalNet.add(Db.dec(item, "net"));
                totalHpp = totalHpp.add(hpp);
                if (Db.dec(item, "net").compareTo(hpp) < 0) {
                    belowCost = true;
                }
            }
            sales.updateTotals(tx, id);

            boolean approvalRequired = false;
            if (!session.isOwner() && belowCost) {
                approvalRequired = true;
                Map<String, Object> approval = tx.queryOne("""
                        SELECT * FROM sale_approvals WHERE sale_id=? AND approved_by IS NOT NULL
                        ORDER BY id DESC LIMIT 1
                        """, id);
                if (sale.get("approved_by") == null && approval == null) {
                    throw ApiException.conflict("Penjualan di bawah harga pokok memerlukan persetujuan owner.");
                }
            }

            for (Map<String, Object> item : items) {
                if ("DAGING".equals(Db.str(item, "item_type"))) {
                    long batchId = Db.lng(item, "meat_batch_id");
                    BigDecimal qty = Db.dec(item, "weight_kg");
                    BigDecimal newRemaining = Db.dec(item, "qty_remaining_kg").subtract(qty);
                    tx.update("""
                            UPDATE meat_batches SET qty_remaining_kg = ?,
                            status = CASE WHEN ? <= 0 THEN 'CLOSED' ELSE status END WHERE id = ?
                            """, newRemaining, newRemaining, batchId);
                    tx.insert("""
                            INSERT INTO meat_movements (batch_id, movement_type, movement_date, qty_kg, unit_cost,
                            total_cost, sale_id, notes, created_by)
                            VALUES (?, 'PENJUALAN', ?, ?, ?, ?, ?, ?, ?)
                            """, batchId, date, qty, Db.dec(item, "batch_hpp"),
                            Money.multiply(qty, Db.dec(item, "batch_hpp")), id,
                            "Penjualan " + Db.str(sale, "doc_no"), session.userId());
                } else {
                    sales.markSold(tx, Db.lng(item, "goat_id"), date, id);
                }
            }
            sales.setStatus(tx, id, "DIKONFIRMASI");
            String revenueAccount = meatSale ? "4102" : "4101";
            String cogsAccount = meatSale ? "5102" : "5101";
            String inventoryAccount = meatSale ? "1303" : "1301";
            List<Journal.Line> lines = new ArrayList<>();
            lines.add(Journal.Line.debit("1201", totalNet, "Piutang pelanggan " + Db.str(sale, "doc_no")));
            lines.add(Journal.Line.credit(revenueAccount, totalNet, "Penjualan " + Db.str(sale, "doc_no")));
            if (totalHpp.signum() > 0) {
                lines.add(Journal.Line.debit(cogsAccount, totalHpp,
                        meatSale ? "HPP daging terjual" : "HPP kambing terjual"));
                lines.add(Journal.Line.credit(inventoryAccount, totalHpp,
                        meatSale ? "Persediaan daging terjual" : "Persediaan kambing terjual"));
            }
            Journal.post(tx, date, Db.lng(sale, "branch_id"), "SALE", id, "SALE:" + id,
                    "Penjualan " + Db.str(sale, "doc_no"), session.userId(), lines);

            BigDecimal advanceUsed = applyCustomerAdvances(tx, id, totalNet, date, session.userId());
            sales.refreshPaymentStatus(tx, id);
            for (long goatId : profitGoats) {
                profits.createSnapshot(tx, goatId, "SEMENTARA", session.userId());
            }
            Audit.log(tx, session, "SALES", "CONFIRM", "sales", String.valueOf(id),
                    Map.of("status", "DRAFT"),
                    Map.of("status", "DIKONFIRMASI", "totalNet", totalNet, "totalHpp", totalHpp,
                            "advanceUsed", advanceUsed, "approvalRequired", approvalRequired), null);
            return Map.<String, Object>of("id", id, "docNo", Db.str(sale, "doc_no"), "status", "DIKONFIRMASI",
                    "totalNet", totalNet, "totalHpp", totalHpp, "advanceUsed", advanceUsed,
                    "approvalRequired", approvalRequired);
        }));
    }

    private BigDecimal applyCustomerAdvances(Db.Tx tx, long saleId, BigDecimal maxAmount, LocalDate date, long userId) {
        List<Map<String, Object>> advances = tx.query("""
                SELECT p.* FROM payments p WHERE p.source_type='ADVANCE_CUSTOMER' AND p.status='POSTED'
                  AND p.source_id=? AND NOT EXISTS (SELECT 1 FROM payment_allocations pa WHERE pa.payment_id=p.id)
                ORDER BY p.payment_date, p.id
                """, saleId);
        BigDecimal remaining = maxAmount;
        for (Map<String, Object> advance : advances) {
            if (remaining.signum() <= 0) {
                break;
            }
            BigDecimal amount = Db.dec(advance, "amount").min(remaining);
            tx.update("INSERT INTO payment_allocations (payment_id, sale_id, amount) VALUES (?, ?, ?)",
                    Db.lng(advance, "id"), saleId, amount);
            Journal.post(tx, date, Db.lng(tx.queryOne("SELECT branch_id FROM sales WHERE id=?", saleId), "branch_id"),
                    "ADVANCE_CUSTOMER_USE", saleId,
                    "ADVANCE_USE:" + Db.lng(advance, "id") + ":" + saleId,
                    "Pemakaian uang muka pelanggan", userId, List.of(
                            Journal.Line.debit("2201", amount, "Uang muka pelanggan"),
                            Journal.Line.credit("1201", amount, "Piutang pelanggan")));
            remaining = remaining.subtract(amount);
        }
        return Money.nz(maxAmount.subtract(remaining));
    }

    public Map<String, Object> requestApproval(SessionInfo session, long id, String reason) {
        session.require(Permissions.SALE_MANAGE);
        if (reason == null || reason.isBlank()) {
            throw ApiException.badRequest("Alasan permintaan persetujuan wajib diisi.");
        }
        return db.withTx(tx -> {
            Map<String, Object> sale = sales.lockSale(tx, id);
            BranchAccess.requireBranch(db, session, Db.lng(sale, "branch_id"));
            if (!"DRAFT".equals(Db.str(sale, "status"))) {
                throw ApiException.conflict("Persetujuan hanya untuk draft penjualan.");
            }
            tx.insert("""
                    INSERT INTO sale_approvals (sale_id, reason, requested_by, draft_version)
                    VALUES (?, ?, ?, ?)
                    """, id, reason, session.userId(), Db.lng(sale, "version"));
            Audit.log(tx, session, "SALES", "REQUEST_APPROVAL", "sales", String.valueOf(id), null,
                    Map.of("reason", reason), reason);
            return Map.<String, Object>of("saleId", id, "requested", true);
        });
    }

    public Map<String, Object> approve(SessionInfo session, long id, String reason) {
        session.require(Permissions.SALE_APPROVE);
        if (!session.isOwner()) {
            throw ApiException.forbidden("Persetujuan penjualan hanya oleh owner.");
        }
        return db.withTx(tx -> {
            Map<String, Object> sale = sales.lockSale(tx, id);
            BranchAccess.requireBranch(db, session, Db.lng(sale, "branch_id"));
            if (!"DRAFT".equals(Db.str(sale, "status"))) {
                throw ApiException.conflict("Persetujuan hanya untuk draft penjualan.");
            }
            tx.update("UPDATE sales SET approved_by=?, approval_reason=? WHERE id=?",
                    session.userId(), reason, id);
            tx.update("""
                    UPDATE sale_approvals SET approved_by=?, approved_at=NOW()
                    WHERE sale_id=? AND approved_by IS NULL ORDER BY id DESC LIMIT 1
                    """, session.userId(), id);
            Audit.log(tx, session, "SALES", "APPROVE", "sales", String.valueOf(id), null,
                    Map.of("approved", true, "reason", reason), reason);
            return Map.<String, Object>of("saleId", id, "approved", true);
        });
    }

    public Map<String, Object> cancel(SessionInfo session, long id, String reason, String requestId) {
        if (!session.isOwner()) {
            throw ApiException.forbidden("Pembatalan penjualan terkonfirmasi hanya oleh owner.");
        }
        if (reason == null || reason.isBlank()) {
            throw ApiException.badRequest("Alasan pembatalan wajib diisi.");
        }
        return db.withTx(tx -> Idempotency.run(tx, requestId, session.userId(), "SALE_CANCEL", () -> {
            Map<String, Object> sale = sales.lockSale(tx, id);
            BranchAccess.requireBranch(db, session, Db.lng(sale, "branch_id"));
            String status = Db.str(sale, "status");
            if (!List.of("DIKONFIRMASI", "SELESAI").contains(status)) {
                throw ApiException.conflict("Hanya penjualan terkonfirmasi yang dapat dibatalkan.");
            }
            LocalDate date = LocalDate.now();
            PeriodGuard.requireOpen(tx, date);
            List<Map<String, Object>> items = sales.lockItems(tx, id);
            List<Map<String, Object>> entries = tx.query("""
                    SELECT id FROM journal_entries WHERE source_id=? AND source_type IN ('SALE','ADVANCE_CUSTOMER_USE')
                    AND status='POSTED' ORDER BY id
                    """, id);
            for (Map<String, Object> entry : entries) {
                Journal.reverse(tx, Db.lng(entry, "id"), date, session.userId(), reason);
            }
            if ("DAGING".equals(Db.str(sale, "sale_type"))) {
                List<Map<String, Object>> movements = tx.query("""
                        SELECT * FROM meat_movements WHERE sale_id=? AND movement_type='PENJUALAN' FOR UPDATE
                        """, id);
                for (Map<String, Object> movement : movements) {
                    tx.update("""
                            UPDATE meat_batches SET qty_remaining_kg = qty_remaining_kg + ?, status='OPEN'
                            WHERE id=?
                            """, Db.dec(movement, "qty_kg"), Db.lng(movement, "batch_id"));
                    tx.update("DELETE FROM meat_movements WHERE id=?", Db.lng(movement, "id"));
                }
            } else {
                for (Map<String, Object> item : items) {
                    long goatId = Db.lng(item, "goat_id");
                    tx.update("""
                            UPDATE goats SET status='AKTIF', sale_readiness='KARANTINA', version=version+1 WHERE id=?
                            """, goatId);
                    sales.releaseLock(tx, goatId);
                    Map<String, Object> lastPlacement = tx.queryOne("""
                            SELECT id FROM pen_placements WHERE goat_id=? ORDER BY entered_at DESC LIMIT 1
                            """, goatId);
                    if (lastPlacement != null) {
                        tx.update("UPDATE pen_placements SET exited_at=NULL WHERE id=?", Db.lng(lastPlacement, "id"));
                    }
                }
            }
            sales.setStatus(tx, id, "DIBATALKAN");
            BigDecimal paid = Db.dec(tx.queryOne("""
                    SELECT COALESCE(SUM(pa.amount),0) AS v FROM payment_allocations pa
                    JOIN payments p ON p.id=pa.payment_id AND p.status='POSTED' WHERE pa.sale_id=?
                    """, id), "v");
            Audit.log(tx, session, "SALES", "CANCEL", "sales", String.valueOf(id),
                    Map.of("status", status), Map.of("status", "DIBATALKAN", "refundRequired", paid), reason);
            return Map.<String, Object>of("id", id, "status", "DIBATALKAN", "refundRequired", paid.signum() > 0,
                    "paidAmount", paid);
        }));
    }

    public Map<String, Object> pendingApprovals(SessionInfo session, Long branchId) {
        session.require(Permissions.SALE_APPROVE);
        Long filter = BranchAccess.filter(db, session, branchId);
        return Map.of("items", sales.pendingApprovals(filter));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> cast(Object value) {
        return value instanceof List<?> list ? (List<Map<String, Object>>) list : List.of();
    }
}
