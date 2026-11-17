package id.peternakan.service.repo;

import id.peternakan.domain.Money;
import id.peternakan.service.api.ApiException;
import id.peternakan.service.db.Db;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class PurchaseRepository {
    private final Db db;

    public PurchaseRepository(Db db) {
        this.db = db;
    }

    public List<Map<String, Object>> list(String q, String status, int page, int size, Long branchId) {
        StringBuilder sql = new StringBuilder("""
                SELECT p.id, p.doc_no, p.purchase_date, p.due_date, p.status, p.total_amount,
                       pt.name AS supplier_name, pt.id AS supplier_id,
                       (SELECT COUNT(*) FROM purchase_items pi WHERE pi.purchase_id = p.id) AS item_count,
                       (SELECT COALESCE(SUM(pi.received_qty),0) FROM purchase_items pi WHERE pi.purchase_id = p.id) AS received_count,
                       (SELECT COALESCE(SUM(ri.acquisition_cost),0) FROM receipt_items ri
                        JOIN receipts r ON r.id = ri.receipt_id WHERE r.purchase_id = p.id) AS received_value,
                       (SELECT COALESCE(SUM(pa.amount),0) FROM payment_allocations pa
                        JOIN payments pay ON pay.id = pa.payment_id AND pay.status = 'POSTED'
                        WHERE pa.purchase_id = p.id) AS paid_value,
                       b.name AS branch_name
                FROM purchases p JOIN parties pt ON pt.id = p.supplier_id
                JOIN branches b ON b.id = p.branch_id WHERE 1=1
                """);
        List<Object> args = new ArrayList<>();
        if (q != null && !q.isBlank()) {
            sql.append(" AND (p.doc_no LIKE ? OR pt.name LIKE ?)");
            String like = "%" + q.trim() + "%";
            args.add(like);
            args.add(like);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND p.status = ?");
            args.add(status);
        }
        if (branchId != null) {
            sql.append(" AND p.branch_id = ?");
            args.add(branchId);
        }
        sql.append(" ORDER BY p.purchase_date DESC, p.id DESC LIMIT ? OFFSET ?");
        args.add(size);
        args.add((page - 1) * size);
        List<Map<String, Object>> rows = db.query(sql.toString(), args.toArray());
        for (Map<String, Object> row : rows) {
            row.put("remaining_payable", Money.nz(Db.dec(row, "received_value").subtract(Db.dec(row, "paid_value"))));
        }
        return rows;
    }

    public long count(String q, String status, Long branchId) {
        StringBuilder sql = new StringBuilder("""
                SELECT COUNT(*) AS n FROM purchases p JOIN parties pt ON pt.id = p.supplier_id WHERE 1=1
                """);
        List<Object> args = new ArrayList<>();
        if (q != null && !q.isBlank()) {
            sql.append(" AND (p.doc_no LIKE ? OR pt.name LIKE ?)");
            String like = "%" + q.trim() + "%";
            args.add(like);
            args.add(like);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND p.status = ?");
            args.add(status);
        }
        if (branchId != null) {
            sql.append(" AND p.branch_id = ?");
            args.add(branchId);
        }
        return db.count(sql.toString(), args.toArray());
    }

    public Map<String, Object> find(long id) {
        Map<String, Object> purchase = db.queryOne("""
                SELECT p.*, pt.name AS supplier_name, pt.phone AS supplier_phone, b.name AS branch_name,
                       (SELECT COALESCE(SUM(ri.acquisition_cost),0) FROM receipt_items ri
                        JOIN receipts r ON r.id = ri.receipt_id WHERE r.purchase_id = p.id) AS received_value,
                       (SELECT COALESCE(SUM(pa.amount),0) FROM payment_allocations pa
                        JOIN payments pay ON pay.id = pa.payment_id AND pay.status = 'POSTED'
                        WHERE pa.purchase_id = p.id) AS paid_value
                FROM purchases p JOIN parties pt ON pt.id = p.supplier_id
                JOIN branches b ON b.id = p.branch_id WHERE p.id = ?
                """, id);
        if (purchase == null) {
            throw ApiException.notFound("Pembelian tidak ditemukan.");
        }
        purchase.put("items", items(id));
        purchase.put("receipts", db.query("""
                SELECT r.id, r.doc_no, r.receipt_date, r.shipping_cost, r.total_cost,
                       (SELECT COUNT(*) FROM receipt_items ri WHERE ri.receipt_id = r.id
                         AND ri.status = 'SEHAT') AS goat_count,
                       (SELECT COUNT(*) FROM receipt_items ri WHERE ri.receipt_id = r.id
                         AND ri.status = 'MATI') AS rejected_count
                FROM receipts r WHERE r.purchase_id = ? ORDER BY r.receipt_date, r.id
                """, id));
        BigDecimal remaining = Db.dec(purchase, "received_value").subtract(Db.dec(purchase, "paid_value"));
        purchase.put("remaining_payable", Money.nz(remaining));
        return purchase;
    }

    public List<Map<String, Object>> items(long purchaseId) {
        return db.query("""
                SELECT id, line_no, description, qty, unit_price, received_qty, rejected_qty,
                       (qty - received_qty - rejected_qty) AS remaining_qty,
                       (unit_price * qty) AS line_total
                FROM purchase_items WHERE purchase_id = ? ORDER BY line_no
                """, purchaseId);
    }

    public long createDraft(Db.Tx tx, String docNo, long branchId, long supplierId, LocalDate date,
                            LocalDate dueDate, String notes, List<Map<String, Object>> items, long userId) {
        BigDecimal total = BigDecimal.ZERO;
        for (Map<String, Object> item : items) {
            BigDecimal price = Money.of(String.valueOf(item.get("unitPrice")));
            int qty = Integer.parseInt(String.valueOf(item.get("qty")));
            if (qty <= 0) {
                throw ApiException.badRequest("Jumlah kambing pada item pembelian harus lebih dari 0.");
            }
            if (price.signum() <= 0) {
                throw ApiException.badRequest("Harga beli harus lebih dari 0.");
            }
            total = total.add(price.multiply(BigDecimal.valueOf(qty)));
        }
        long id = tx.insert("""
                INSERT INTO purchases (doc_no, branch_id, supplier_id, purchase_date, due_date, status, notes,
                total_amount, created_by) VALUES (?, ?, ?, ?, ?, 'DRAFT', ?, ?, ?)
                """, docNo, branchId, supplierId, date, dueDate, notes, Money.nz(total), userId);
        int line = 1;
        for (Map<String, Object> item : items) {
            tx.update("""
                    INSERT INTO purchase_items (purchase_id, line_no, description, qty, unit_price)
                    VALUES (?, ?, ?, ?, ?)
                    """, id, line++,
                    String.valueOf(item.getOrDefault("description", "Kambing")),
                    Integer.parseInt(String.valueOf(item.get("qty"))),
                    Money.of(String.valueOf(item.get("unitPrice"))));
        }
        return id;
    }

    public void setStatus(Db.Tx tx, long id, String status) {
        tx.update("UPDATE purchases SET status = ?, version = version + 1 WHERE id = ?", status, id);
        if ("DIKONFIRMASI".equals(status)) {
            tx.update("UPDATE purchases SET confirmed_at = NOW() WHERE id = ?", id);
        }
    }

    public Map<String, Object> lockPurchase(Db.Tx tx, long id) {
        Map<String, Object> p = tx.queryOne("SELECT * FROM purchases WHERE id = ? FOR UPDATE", id);
        if (p == null) {
            throw ApiException.notFound("Pembelian tidak ditemukan.");
        }
        return p;
    }

    public void addReceived(Db.Tx tx, long itemId, int qty) {
        tx.update("UPDATE purchase_items SET received_qty = received_qty + ? WHERE id = ?", qty, itemId);
    }

    public void addRejected(Db.Tx tx, long itemId, int qty) {
        tx.update("UPDATE purchase_items SET rejected_qty = rejected_qty + ? WHERE id = ?", qty, itemId);
    }

    public void refreshStatus(Db.Tx tx, long purchaseId) {
        Map<String, Object> row = tx.queryOne("""
                SELECT SUM(qty) AS ordered, SUM(received_qty) AS received, SUM(rejected_qty) AS rejected
                FROM purchase_items WHERE purchase_id = ?
                """, purchaseId);
        int ordered = row == null || row.get("ordered") == null ? 0 : ((Number) row.get("ordered")).intValue();
        int received = row == null || row.get("received") == null ? 0 : ((Number) row.get("received")).intValue();
        int rejected = row == null || row.get("rejected") == null ? 0 : ((Number) row.get("rejected")).intValue();
        String status = received + rejected == 0 ? "DIKONFIRMASI"
                : received + rejected >= ordered ? "SELESAI" : "DITERIMA_SEBAGIAN";
        tx.update("UPDATE purchases SET status = ? WHERE id = ?", status, purchaseId);
    }

    public List<Map<String, Object>> openForReceipt(Long branchId) {
        return db.query("""
                SELECT p.id, p.doc_no, p.purchase_date, p.status, pt.name AS supplier_name,
                       b.name AS branch_name
                FROM purchases p JOIN parties pt ON pt.id = p.supplier_id
                JOIN branches b ON b.id = p.branch_id
                WHERE p.status IN ('DIKONFIRMASI','DITERIMA_SEBAGIAN')
                  AND (? IS NULL OR p.branch_id = ?)
                ORDER BY p.purchase_date DESC
                """, branchId, branchId);
    }
}
