package id.peternakan.service.svc;

import id.peternakan.domain.AllocationCalculator;
import id.peternakan.domain.Money;
import id.peternakan.domain.Permissions;
import id.peternakan.service.api.ApiException;
import id.peternakan.service.db.Db;
import id.peternakan.service.repo.GoatRepository;
import id.peternakan.service.repo.PenRepository;
import id.peternakan.service.repo.PurchaseRepository;
import id.peternakan.service.repo.ReceiptRepository;
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

public final class ReceiptService {
    private final Db db;
    private final ReceiptRepository receipts;
    private final PurchaseRepository purchases;
    private final GoatRepository goats;
    private final PenRepository pens;

    public ReceiptService(Db db, ReceiptRepository receipts, PurchaseRepository purchases, GoatRepository goats,
                          PenRepository pens) {
        this.db = db;
        this.receipts = receipts;
        this.purchases = purchases;
        this.goats = goats;
        this.pens = pens;
    }

    public Map<String, Object> list(SessionInfo session, String q, int page, int size, Long branchId) {
        session.require(Permissions.RECEIPT_MANAGE);
        Long filter = BranchAccess.filter(db, session, branchId);
        return Map.of("items", receipts.list(q, page, size, filter), "total", receipts.count(q, filter));
    }

    public Map<String, Object> find(SessionInfo session, long id) {
        session.require(Permissions.RECEIPT_MANAGE);
        Map<String, Object> receipt = receipts.find(id);
        BranchAccess.requireBranch(db, session, Db.lng(receipt, "branch_id"));
        return receipt;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> create(SessionInfo session, Map<String, Object> body, String requestId) {
        session.require(Permissions.RECEIPT_MANAGE);
        long purchaseId = Long.parseLong(String.valueOf(body.get("purchaseId")));
        LocalDate date = LocalDate.parse(String.valueOf(body.get("receiptDate")));
        BigDecimal shipping = Money.of(String.valueOf(body.getOrDefault("shippingCost", "0")));
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
        if (items == null || items.isEmpty()) {
            throw ApiException.badRequest("Penerimaan harus memuat minimal satu kambing.");
        }
        return db.withTx(tx -> Idempotency.run(tx, requestId, session.userId(), "RECEIPT_CREATE", () -> {
            PeriodGuard.requireOpen(tx, date);
            Map<String, Object> purchase = purchases.lockPurchase(tx, purchaseId);
            String purchaseStatus = Db.str(purchase, "status");
            if (!List.of("DIKONFIRMASI", "DITERIMA_SEBAGIAN").contains(purchaseStatus)) {
                String hint = switch (purchaseStatus) {
                    case "DRAFT" -> " Konfirmasi pembelian terlebih dahulu.";
                    case "SELESAI" -> " Pembelian sudah selesai diterima.";
                    case "DIBATALKAN" -> " Pembelian sudah dibatalkan.";
                    default -> "";
                };
                throw ApiException.conflict("Pembelian berstatus " + purchaseStatus
                        + " tidak dapat menerima kambing." + hint);
            }
            long supplierId = Db.lng(purchase, "supplier_id");
            long branchId = Db.lng(purchase, "branch_id");
            BranchAccess.requireBranch(db, session, branchId);
            String docNo = DocNo.next(tx, "TR", date);
            long receiptId = receipts.insert(tx, docNo, branchId, purchaseId, date, shipping,
                    PartyService.str(body, "notes"), Money.ZERO, session.userId());

            // Validasi item dan hitung biaya perolehan per kambing.
            Map<Long, Map<String, Object>> purchaseItems = new HashMap<>();
            Map<Long, Integer> receivedNow = new HashMap<>();
            for (Map<String, Object> row : items) {
                long purchaseItemId = Long.parseLong(String.valueOf(row.get("purchaseItemId")));
                Map<String, Object> pi = tx.queryOne("SELECT * FROM purchase_items WHERE id=? AND purchase_id=? FOR UPDATE",
                        purchaseItemId, purchaseId);
                if (pi == null) {
                    throw ApiException.badRequest("Item pembelian tidak ditemukan pada dokumen ini.");
                }
                int remaining = ((Number) pi.get("qty")).intValue()
                        - ((Number) pi.get("received_qty")).intValue()
                        - ((Number) pi.get("rejected_qty")).intValue();
                int used = receivedNow.getOrDefault(purchaseItemId, 0) + 1;
                if (used > remaining) {
                    throw ApiException.conflict("Jumlah terima melebihi pesanan untuk item "
                            + Db.str(pi, "description") + ". Sisa " + remaining + ".");
                }
                receivedNow.put(purchaseItemId, used);
                purchaseItems.put(purchaseItemId, pi);
            }

            // Status per baris: SEHAT atau MATI. Sakit dianggap sehat.
            Map<Integer, String> statuses = new HashMap<>();
            List<Integer> acceptedIndexes = new ArrayList<>();
            for (int i = 0; i < items.size(); i++) {
                String status = rowStatus(items.get(i));
                statuses.put(i, status);
                if ("SEHAT".equals(status)) {
                    acceptedIndexes.add(i);
                }
            }

            // Biaya barang per kambing = harga satuan item.
            Map<Long, BigDecimal> goodsCost = new HashMap<>();
            for (Map.Entry<Long, Map<String, Object>> entry : purchaseItems.entrySet()) {
                goodsCost.put(entry.getKey(), Money.nz(Db.dec(entry.getValue(), "unit_price")));
            }

            // Biaya kirim hanya dibagi ke kambing yang benar-benar diterima.
            Map<Integer, BigDecimal> shippingShares = new HashMap<>();
            if (shipping.signum() > 0 && !acceptedIndexes.isEmpty()) {
                List<AllocationCalculator.WeightedGoat> weightGoats = new ArrayList<>();
                for (int i : acceptedIndexes) {
                    weightGoats.add(new AllocationCalculator.WeightedGoat(i,
                            String.valueOf(items.get(i).getOrDefault("goatCode", "")), 1));
                }
                for (AllocationCalculator.Allocation allocation
                        : AllocationCalculator.allocateEvenly(shipping, weightGoats)) {
                    shippingShares.put((int) allocation.goatId(), allocation.amount());
                }
            }

            BigDecimal goodsTotal = Money.ZERO;
            BigDecimal inventoryTotal = Money.ZERO;
            List<String> createdCodes = new ArrayList<>();
            int rejectedCount = 0;
            for (int i = 0; i < items.size(); i++) {
                Map<String, Object> row = items.get(i);
                long piId = Long.parseLong(String.valueOf(row.get("purchaseItemId")));
                Map<String, Object> pi = purchaseItems.get(piId);
                String status = statuses.get(i);
                if ("MATI".equals(status)) {
                    receipts.insertItem(tx, receiptId, piId, null, null, null, null, Money.ZERO, "MATI");
                    purchases.addRejected(tx, piId, 1);
                    rejectedCount++;
                    continue;
                }

                BigDecimal goods = goodsCost.get(piId);
                BigDecimal shipShare = shippingShares.getOrDefault(i, Money.ZERO);
                BigDecimal acquisition = Money.nz(goods.add(shipShare));
                String code = String.valueOf(row.get("goatCode"));
                if (code.isBlank()) {
                    throw ApiException.badRequest("Kode kambing wajib diisi untuk status Sehat.");
                }
                BigDecimal weight = Money.of(String.valueOf(row.get("weight")));
                long penId = Long.parseLong(String.valueOf(row.get("penId")));
                Map<String, Object> pen = tx.queryOne("SELECT * FROM pens WHERE id = ?", penId);
                if (pen == null) {
                    throw ApiException.notFound("Kandang tidak ditemukan.");
                }
                if (Db.lng(pen, "branch_id") != branchId) {
                    throw ApiException.conflict("Kandang tujuan harus berada di cabang pembelian.");
                }
                pens.ensureCapacity(tx, penId);
                String gender = String.valueOf(row.getOrDefault("gender", "JANTAN"));
                String jenis = PartyService.str(row, "jenis");
                if (jenis == null || jenis.isBlank()) {
                    jenis = Db.str(pi, "description").toLowerCase(java.util.Locale.ROOT).contains("domba")
                            ? "Domba" : "Kambing";
                }
                long goatId = goats.insert(tx, code, null, supplierId, jenis, gender,
                        PartyService.str(row, "birthDate") == null ? null : LocalDate.parse(PartyService.str(row, "birthDate")),
                        row.get("estimatedAge") == null ? null : Integer.parseInt(String.valueOf(row.get("estimatedAge"))),
                        date, weight, acquisition, PartyService.str(row, "physicalMarks"),
                        PartyService.str(row, "notes"), branchId);
                long receiptItemId = receipts.insertItem(tx, receiptId, piId, goatId, weight,
                        PartyService.str(row, "condition"), penId, acquisition, "SEHAT");
                tx.update("UPDATE goats SET receipt_item_id=? WHERE id=?", receiptItemId, goatId);
                pens.insertPlacement(tx, goatId, penId, date.atTime(8, 0), session.userId(), "Penerimaan " + docNo);
                purchases.addReceived(tx, piId, 1);
                goodsTotal = goodsTotal.add(goods);
                inventoryTotal = inventoryTotal.add(acquisition);
                createdCodes.add(code);
            }
            tx.update("UPDATE receipts SET total_cost=? WHERE id=?", inventoryTotal, receiptId);
            purchases.refreshStatus(tx, purchaseId);

            List<Journal.Line> lines = new ArrayList<>();
            if (inventoryTotal.signum() > 0) {
                lines.add(Journal.Line.debit("1301", inventoryTotal, "Persediaan kambing diterima " + docNo));
            }
            if (goodsTotal.signum() > 0) {
                lines.add(Journal.Line.credit("2101", goodsTotal, "Utang supplier " + docNo));
            }
            if (shipping.signum() > 0) {
                lines.add(Journal.Line.credit("1101", shipping, "Pengiriman pembelian " + docNo));
                if (acceptedIndexes.isEmpty()) {
                    lines.add(Journal.Line.debit("6102", shipping, "Biaya kirim tanpa kambing diterima " + docNo));
                }
                String payDoc = DocNo.next(tx, "BY", date);
                long paymentId = tx.insert("""
                        INSERT INTO payments (doc_no, branch_id, direction, party_id, payment_date, amount, method,
                        source_type, source_id, payment_type, notes, created_by)
                        VALUES (?, ?, 'OUT', ?, ?, ?, 'TUNAI', 'PURCHASE_SHIPPING', ?, 'PELUNASAN', ?, ?)
                        """, payDoc, branchId, supplierId, date, shipping, receiptId,
                        "Biaya pengiriman " + docNo, session.userId());
                if (!acceptedIndexes.isEmpty()) {
                    tx.update("""
                            INSERT INTO payment_allocations (payment_id, purchase_id, amount) VALUES (?, ?, ?)
                            """, paymentId, purchaseId, shipping);
                }
            }
            if (!lines.isEmpty()) {
                Journal.post(tx, date, branchId, "RECEIPT", receiptId, "RECEIPT:" + receiptId,
                        "Penerimaan kambing " + docNo, session.userId(), lines);
            }

            // Gunakan uang muka supplier yang belum dialokasikan untuk tagihan ini.
            BigDecimal unusedAdvance = applySupplierAdvances(tx, purchaseId, supplierId, goodsTotal, date,
                    session.userId(), receiptId, branchId);
            Audit.log(tx, session, "RECEIPTS", "CREATE", "receipts", String.valueOf(receiptId), null,
                    Map.of("docNo", docNo, "goatCodes", createdCodes, "goodsTotal", goodsTotal,
                            "shipping", shipping, "inventoryTotal", inventoryTotal,
                            "rejectedCount", rejectedCount, "unusedAdvance", unusedAdvance), null);
            return Map.<String, Object>of("id", receiptId, "docNo", docNo, "goatCodes", createdCodes,
                    "inventoryTotal", inventoryTotal, "rejectedCount", rejectedCount,
                    "unusedAdvance", unusedAdvance);
        }));
    }

    private static String rowStatus(Map<String, Object> row) {
        Object value = row.get("status");
        if (value == null || String.valueOf(value).isBlank()) {
            value = row.get("condition");
        }
        return value != null && "MATI".equalsIgnoreCase(String.valueOf(value).trim()) ? "MATI" : "SEHAT";
    }

    private BigDecimal applySupplierAdvances(Db.Tx tx, long purchaseId, long supplierId, BigDecimal maxAmount,
                                             LocalDate date, long userId, long receiptId, long branchId) {
        List<Map<String, Object>> advances = tx.query("""
                SELECT p.*, COALESCE(pa.amount,0) AS allocated_amount, pa.id AS allocation_id
                FROM payments p
                LEFT JOIN payment_allocations pa ON pa.payment_id = p.id
                WHERE p.source_type='ADVANCE_SUPPLIER' AND p.status='POSTED'
                  AND p.party_id=?
                  AND (pa.id IS NULL OR pa.purchase_id = ?)
                  AND p.amount > COALESCE(pa.amount,0)
                ORDER BY p.payment_date, p.id
                """, supplierId, purchaseId);
        BigDecimal available = BigDecimal.ZERO;
        for (Map<String, Object> advance : advances) {
            available = available.add(Db.dec(advance, "amount").subtract(Db.dec(advance, "allocated_amount")));
        }
        BigDecimal remaining = maxAmount;
        for (Map<String, Object> advance : advances) {
            if (remaining.signum() <= 0) {
                break;
            }
            BigDecimal unallocated = Db.dec(advance, "amount").subtract(Db.dec(advance, "allocated_amount"));
            BigDecimal amount = unallocated.min(remaining);
            if (advance.get("allocation_id") == null) {
                tx.update("""
                        INSERT INTO payment_allocations (payment_id, purchase_id, amount) VALUES (?, ?, ?)
                        """, Db.lng(advance, "id"), purchaseId, amount);
            } else {
                tx.update("""
                        UPDATE payment_allocations SET amount = amount + ? WHERE id = ?
                        """, amount, Db.lng(advance, "allocation_id"));
            }
            Journal.post(tx, date, branchId, "ADVANCE_SUPPLIER_USE", purchaseId,
                    "ADVANCE_USE:" + Db.lng(advance, "id") + ":" + purchaseId + ":" + receiptId,
                    "Pemakaian uang muka supplier", userId, List.of(
                            Journal.Line.debit("2101", amount, "Utang supplier"),
                            Journal.Line.credit("1401", amount, "Uang muka supplier")));
            remaining = remaining.subtract(amount);
        }
        return Money.nz(available.subtract(maxAmount.subtract(remaining)));
    }
}
