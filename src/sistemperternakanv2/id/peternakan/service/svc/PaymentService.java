package id.peternakan.service.svc;

import id.peternakan.domain.Money;
import id.peternakan.domain.Permissions;
import id.peternakan.service.api.ApiException;
import id.peternakan.service.db.Db;
import id.peternakan.service.repo.PaymentRepository;
import id.peternakan.service.repo.PurchaseRepository;
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
import java.util.List;
import java.util.Map;

public final class PaymentService {
    private final Db db;
    private final PaymentRepository payments;
    private final SaleRepository sales;
    private final PurchaseRepository purchases;

    public PaymentService(Db db, PaymentRepository payments, SaleRepository sales, PurchaseRepository purchases) {
        this.db = db;
        this.payments = payments;
        this.sales = sales;
        this.purchases = purchases;
    }

    public Map<String, Object> list(SessionInfo session, String q, String direction, String from, String to,
                                    int page, int size, Long branchId) {
        session.require(Permissions.PAYMENT_RECORD);
        Long filter = BranchAccess.filter(db, session, branchId);
        return Map.of("items", payments.list(q, direction, from, to, page, size, filter),
                "total", payments.count(q, direction, from, to, filter));
    }

    public Map<String, Object> find(SessionInfo session, long id) {
        session.require(Permissions.PAYMENT_RECORD);
        Map<String, Object> payment = payments.find(id);
        BranchAccess.requireBranch(db, session, Db.lng(payment, "branch_id"));
        return payment;
    }

    public Map<String, Object> create(SessionInfo session, Map<String, Object> body, String requestId) {
        session.require(Permissions.PAYMENT_RECORD);
        String sourceType = String.valueOf(body.get("sourceType"));
        String direction = String.valueOf(body.getOrDefault("direction", "IN"));
        LocalDate date = LocalDate.parse(String.valueOf(body.get("paymentDate")));
        BigDecimal amount = Money.of(String.valueOf(body.get("amount")));
        Long partyId = body.get("partyId") == null ? null : Long.parseLong(String.valueOf(body.get("partyId")));
        Long sourceId = body.get("sourceId") == null ? null : Long.parseLong(String.valueOf(body.get("sourceId")));
        String method = body.get("method") == null ? "TUNAI" : String.valueOf(body.get("method"));
        String paymentType = body.get("paymentType") == null ? "PELUNASAN" : String.valueOf(body.get("paymentType"));
        String reference = PartyService.str(body, "referenceNo");
        String notes = PartyService.str(body, "notes");
        if (amount.signum() <= 0) {
            throw ApiException.badRequest("Jumlah pembayaran harus lebih dari 0.");
        }
        long defaultBranch = BranchAccess.requireWrite(db, session, PartyService.longVal(body, "branchId"));
        return db.withTx(tx -> Idempotency.run(tx, requestId, session.userId(), "PAYMENT_CREATE", () -> {
            PeriodGuard.requireOpen(tx, date);
            String cashCode = "TRANSFER".equals(method) ? "1102" : "1101";
            String docNo = DocNo.next(tx, "BY", date);
            long paymentId;
            switch (sourceType) {
                case "SALE" -> {
                    if (sourceId == null) {
                        throw ApiException.badRequest("Dokumen penjualan wajib dipilih.");
                    }
                    Map<String, Object> sale = sales.lockSale(tx, sourceId);
                    if (!List.of("DIKONFIRMASI", "SELESAI").contains(Db.str(sale, "status"))) {
                        throw ApiException.conflict("Pembayaran hanya untuk penjualan terkonfirmasi.");
                    }
                    BigDecimal remaining = Db.dec(sale, "total_net").subtract(payments.paidForSale(sourceId));
                    if (amount.compareTo(remaining) > 0) {
                        throw ApiException.conflict("Pembayaran melebihi sisa tagihan " + Money.format(remaining) + ".");
                    }
                    long saleBranch = Db.lng(sale, "branch_id");
                    BranchAccess.requireBranch(db, session, saleBranch);
                    paymentId = payments.insert(tx, docNo, saleBranch, "IN", Db.lng(sale, "customer_id"),
                            date, amount, method, reference, "SALE", sourceId, "PELUNASAN", notes,
                            session.userId());
                    payments.allocate(tx, paymentId, sourceId, null, amount);
                    Journal.post(tx, date, saleBranch, "PAYMENT", paymentId, "PAYMENT:" + paymentId,
                            "Pelunasan penjualan " + Db.str(sale, "doc_no"), session.userId(), List.of(
                                    Journal.Line.debit(cashCode, amount, "Kas/bank diterima"),
                                    Journal.Line.credit("1201", amount, "Piutang pelanggan")));
                    sales.refreshPaymentStatus(tx, sourceId);
                    Audit.log(tx, session, "PAYMENTS", "CREATE", "payments", String.valueOf(paymentId), null,
                            Map.of("docNo", docNo, "saleId", sourceId, "amount", amount), null);
                }
                case "PURCHASE" -> {
                    if (sourceId == null) {
                        throw ApiException.badRequest("Dokumen pembelian wajib dipilih.");
                    }
                    Map<String, Object> purchase = purchases.lockPurchase(tx, sourceId);
                    if (!List.of("DITERIMA_SEBAGIAN", "SELESAI").contains(Db.str(purchase, "status"))) {
                        throw ApiException.conflict("Pembayaran hanya untuk pembelian yang sudah diterima.");
                    }
                    BigDecimal received = Db.dec(tx.queryOne("""
                            SELECT COALESCE(SUM(ri.acquisition_cost),0) AS v FROM receipt_items ri
                            JOIN receipts r ON r.id=ri.receipt_id WHERE r.purchase_id=?
                            """, sourceId), "v");
                    BigDecimal remaining = received.subtract(payments.paidForPurchase(sourceId));
                    if (amount.compareTo(remaining) > 0) {
                        throw ApiException.conflict("Pembayaran melebihi sisa utang " + Money.format(remaining) + ".");
                    }
                    long purchaseBranch = Db.lng(purchase, "branch_id");
                    BranchAccess.requireBranch(db, session, purchaseBranch);
                    paymentId = payments.insert(tx, docNo, purchaseBranch, "OUT", Db.lng(purchase, "supplier_id"),
                            date, amount, method, reference, "PURCHASE", sourceId, "PELUNASAN", notes,
                            session.userId());
                    payments.allocate(tx, paymentId, null, sourceId, amount);
                    Journal.post(tx, date, purchaseBranch, "PAYMENT", paymentId, "PAYMENT:" + paymentId,
                            "Pembayaran utang supplier " + Db.str(purchase, "doc_no"), session.userId(), List.of(
                                    Journal.Line.debit("2101", amount, "Utang supplier"),
                                    Journal.Line.credit(cashCode, amount, "Kas/bank keluar")));
                    Audit.log(tx, session, "PAYMENTS", "CREATE", "payments", String.valueOf(paymentId), null,
                            Map.of("docNo", docNo, "purchaseId", sourceId, "amount", amount), null);
                }
                case "ADVANCE_CUSTOMER" -> {
                    if (partyId == null) {
                        throw ApiException.badRequest("Pelanggan wajib dipilih.");
                    }
                    if (sourceId != null) {
                        Map<String, Object> sale = tx.queryOne("SELECT * FROM sales WHERE id=? AND customer_id=?", sourceId, partyId);
                        if (sale == null || !"DRAFT".equals(Db.str(sale, "status"))) {
                            throw ApiException.conflict("Uang muka pelanggan harus merujuk draft penjualan pelanggan tersebut.");
                        }
                    }
                    paymentId = payments.insert(tx, docNo, defaultBranch, "IN", partyId, date, amount, method,
                            reference, "ADVANCE_CUSTOMER", sourceId, "UANG_MUKA", notes, session.userId());
                    Journal.post(tx, date, defaultBranch, "PAYMENT", paymentId, "PAYMENT:" + paymentId,
                            "Uang muka pelanggan", session.userId(), List.of(
                                    Journal.Line.debit(cashCode, amount, "Kas/bank diterima"),
                                    Journal.Line.credit("2201", amount, "Uang muka pelanggan")));
                    Audit.log(tx, session, "PAYMENTS", "CREATE_ADVANCE_CUSTOMER", "payments",
                            String.valueOf(paymentId), null, Map.of("docNo", docNo, "amount", amount), null);
                }
                case "ADVANCE_SUPPLIER" -> {
                    if (partyId == null) {
                        throw ApiException.badRequest("Supplier wajib dipilih.");
                    }
                    paymentId = payments.insert(tx, docNo, defaultBranch, "OUT", partyId, date, amount, method,
                            reference, "ADVANCE_SUPPLIER", sourceId, "UANG_MUKA", notes, session.userId());
                    Journal.post(tx, date, defaultBranch, "PAYMENT", paymentId, "PAYMENT:" + paymentId,
                            "Uang muka supplier", session.userId(), List.of(
                                    Journal.Line.debit("1401", amount, "Uang muka supplier"),
                                    Journal.Line.credit(cashCode, amount, "Kas/bank keluar")));
                    Audit.log(tx, session, "PAYMENTS", "CREATE_ADVANCE_SUPPLIER", "payments",
                            String.valueOf(paymentId), null, Map.of("docNo", docNo, "amount", amount), null);
                }
                case "REFUND" -> {
                    if (!session.isOwner()) {
                        throw ApiException.forbidden("Refund hanya dapat dilakukan owner.");
                    }
                    String refundType = PartyService.str(body, "refundType");
                    if ("SUPPLIER".equals(refundType)) {
                        paymentId = payments.insert(tx, docNo, defaultBranch, "IN", partyId, date, amount, method,
                                reference, "REFUND", sourceId, "REFUND", notes, session.userId());
                        Journal.post(tx, date, defaultBranch, "PAYMENT", paymentId, "PAYMENT:" + paymentId,
                                "Refund uang muka supplier", session.userId(), List.of(
                                        Journal.Line.debit(cashCode, amount, "Kas/bank diterima"),
                                        Journal.Line.credit("1401", amount, "Uang muka supplier")));
                    } else {
                        paymentId = payments.insert(tx, docNo, defaultBranch, "OUT", partyId, date, amount, method,
                                reference, "REFUND", sourceId, "REFUND", notes, session.userId());
                        Journal.post(tx, date, defaultBranch, "PAYMENT", paymentId, "PAYMENT:" + paymentId,
                                "Refund pelanggan", session.userId(), List.of(
                                        Journal.Line.debit("1201", amount, "Piutang pelanggan"),
                                        Journal.Line.credit(cashCode, amount, "Kas/bank keluar")));
                    }
                    Audit.log(tx, session, "PAYMENTS", "REFUND", "payments", String.valueOf(paymentId), null,
                            Map.of("docNo", docNo, "amount", amount), null);
                }
                default -> throw ApiException.badRequest("Jenis sumber pembayaran tidak dikenal: " + sourceType);
            }
            return Map.<String, Object>of("id", paymentId, "docNo", docNo, "amount", amount);
        }));
    }

    public Map<String, Object> reverse(SessionInfo session, long id, String reason, String requestId) {
        if (!session.isOwner()) {
            throw ApiException.forbidden("Pembatalan pembayaran hanya oleh owner.");
        }
        if (reason == null || reason.isBlank()) {
            throw ApiException.badRequest("Alasan pembatalan pembayaran wajib diisi.");
        }
        return db.withTx(tx -> Idempotency.run(tx, requestId, session.userId(), "PAYMENT_REVERSE", () -> {
            Map<String, Object> payment = tx.queryOne("SELECT * FROM payments WHERE id=? FOR UPDATE", id);
            if (payment == null) {
                throw ApiException.notFound("Pembayaran tidak ditemukan.");
            }
            BranchAccess.requireBranch(db, session, Db.lng(payment, "branch_id"));
            if ("REVERSED".equals(Db.str(payment, "status"))) {
                throw ApiException.conflict("Pembayaran sudah dibatalkan.");
            }
            LocalDate date = LocalDate.now();
            PeriodGuard.requireOpen(tx, date);
            List<Map<String, Object>> entries = tx.query("""
                    SELECT id FROM journal_entries WHERE source_type='PAYMENT' AND source_id=? AND status='POSTED'
                    """, id);
            for (Map<String, Object> entry : entries) {
                Journal.reverse(tx, Db.lng(entry, "id"), date, session.userId(), reason);
            }
            tx.update("UPDATE payments SET status='REVERSED', reversed_by_id=? WHERE id=?",
                    session.userId(), id);
            Map<String, Object> allocation = tx.queryOne("SELECT * FROM payment_allocations WHERE payment_id=?", id);
            if (allocation != null) {
                if (allocation.get("sale_id") != null) {
                    sales.refreshPaymentStatus(tx, ((Number) allocation.get("sale_id")).longValue());
                }
            }
            Audit.log(tx, session, "PAYMENTS", "REVERSE", "payments", String.valueOf(id), payment,
                    Map.of("status", "REVERSED"), reason);
            return Map.<String, Object>of("id", id, "status", "REVERSED");
        }));
    }

    public Map<String, Object> receivables(SessionInfo session, Long branchId) {
        session.require(Permissions.PAYMENT_RECORD);
        Long filter = BranchAccess.filter(db, session, branchId);
        return Map.of("items", payments.receivables(filter));
    }

    public Map<String, Object> payables(SessionInfo session, Long branchId) {
        session.require(Permissions.PAYMENT_RECORD);
        Long filter = BranchAccess.filter(db, session, branchId);
        return Map.of("items", payments.payables(filter));
    }
}
