package id.peternakan.service.repo;

import id.peternakan.domain.Money;
import id.peternakan.service.api.ApiException;
import id.peternakan.service.db.Db;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class ReceiptRepository {
    private final Db db;

    public ReceiptRepository(Db db) {
        this.db = db;
    }

    public List<Map<String, Object>> list(String q, int page, int size, Long branchId) {
        StringBuilder sql = new StringBuilder("""
                SELECT r.id, r.doc_no, r.receipt_date, r.shipping_cost, r.total_cost,
                       p.doc_no AS purchase_doc_no, pt.name AS supplier_name, b.name AS branch_name,
                       (SELECT COUNT(*) FROM receipt_items ri WHERE ri.receipt_id = r.id) AS goat_count
                FROM receipts r JOIN purchases p ON p.id = r.purchase_id
                JOIN parties pt ON pt.id = p.supplier_id
                JOIN branches b ON b.id = r.branch_id WHERE 1=1
                """);
        java.util.List<Object> args = new java.util.ArrayList<>();
        if (q != null && !q.isBlank()) {
            sql.append(" AND (r.doc_no LIKE ? OR p.doc_no LIKE ? OR pt.name LIKE ?)");
            String like = "%" + q.trim() + "%";
            args.add(like);
            args.add(like);
            args.add(like);
        }
        if (branchId != null) {
            sql.append(" AND r.branch_id = ?");
            args.add(branchId);
        }
        sql.append(" ORDER BY r.receipt_date DESC, r.id DESC LIMIT ? OFFSET ?");
        args.add(size);
        args.add((page - 1) * size);
        return db.query(sql.toString(), args.toArray());
    }

    public long count(String q, Long branchId) {
        StringBuilder sql = new StringBuilder("""
                SELECT COUNT(*) AS n FROM receipts r JOIN purchases p ON p.id = r.purchase_id
                JOIN parties pt ON pt.id = p.supplier_id WHERE 1=1
                """);
        java.util.List<Object> args = new java.util.ArrayList<>();
        if (q != null && !q.isBlank()) {
            sql.append(" AND (r.doc_no LIKE ? OR p.doc_no LIKE ? OR pt.name LIKE ?)");
            String like = "%" + q.trim() + "%";
            args.add(like);
            args.add(like);
            args.add(like);
        }
        if (branchId != null) {
            sql.append(" AND r.branch_id = ?");
            args.add(branchId);
        }
        return db.count(sql.toString(), args.toArray());
    }

    public Map<String, Object> find(long id) {
        Map<String, Object> receipt = db.queryOne("""
                SELECT r.*, p.doc_no AS purchase_doc_no, pt.name AS supplier_name, b.name AS branch_name
                FROM receipts r JOIN purchases p ON p.id = r.purchase_id
                JOIN parties pt ON pt.id = p.supplier_id
                JOIN branches b ON b.id = r.branch_id WHERE r.id = ?
                """, id);
        if (receipt == null) {
            throw ApiException.notFound("Penerimaan tidak ditemukan.");
        }
        receipt.put("items", db.query("""
                SELECT ri.*, g.code AS goat_code, pen.name AS pen_name
                FROM receipt_items ri LEFT JOIN goats g ON g.id = ri.goat_id
                LEFT JOIN pens pen ON pen.id = ri.pen_id WHERE ri.receipt_id = ? ORDER BY ri.id
                """, id));
        return receipt;
    }

    public long insert(Db.Tx tx, String docNo, long branchId, long purchaseId, LocalDate date, BigDecimal shipping,
                       String notes, BigDecimal totalCost, long userId) {
        return tx.insert("""
                INSERT INTO receipts (doc_no, branch_id, purchase_id, receipt_date, shipping_cost, notes,
                total_cost, created_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, docNo, branchId, purchaseId, date, Money.nz(shipping), notes, Money.nz(totalCost), userId);
    }

    public long insertItem(Db.Tx tx, long receiptId, long purchaseItemId, Long goatId, BigDecimal weight,
                           String condition, Long penId, BigDecimal acquisitionCost, String status) {
        return tx.insert("""
                INSERT INTO receipt_items (receipt_id, purchase_item_id, goat_id, status, weight_kg,
                condition_note, pen_id, acquisition_cost) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, receiptId, purchaseItemId, goatId, status, weight, condition, penId,
                Money.nz(acquisitionCost));
    }
}
