package id.peternakan.service.repo;

import id.peternakan.domain.Money;
import id.peternakan.service.api.ApiException;
import id.peternakan.service.db.Db;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class PaymentRepository {
    private final Db db;

    public PaymentRepository(Db db) {
        this.db = db;
    }

    public List<Map<String, Object>> list(String q, String direction, String from, String to, int page, int size,
                                          Long branchId) {
        StringBuilder sql = new StringBuilder("""
                SELECT p.id, p.doc_no, p.direction, p.payment_date, p.amount, p.method, p.source_type,
                       p.payment_type, p.status, p.reference_no, pt.name AS party_name,
                       s.doc_no AS sale_doc_no, pu.doc_no AS purchase_doc_no, b.name AS branch_name
                FROM payments p
                LEFT JOIN parties pt ON pt.id = p.party_id
                JOIN branches b ON b.id = p.branch_id
                LEFT JOIN payment_allocations pa ON pa.payment_id = p.id
                LEFT JOIN sales s ON s.id = pa.sale_id
                LEFT JOIN purchases pu ON pu.id = pa.purchase_id
                WHERE 1=1
                """);
        List<Object> args = new ArrayList<>();
        if (q != null && !q.isBlank()) {
            sql.append(" AND (p.doc_no LIKE ? OR pt.name LIKE ? OR p.reference_no LIKE ?)");
            String like = "%" + q.trim() + "%";
            args.add(like);
            args.add(like);
            args.add(like);
        }
        if (direction != null && !direction.isBlank()) {
            sql.append(" AND p.direction = ?");
            args.add(direction);
        }
        if (from != null && !from.isBlank()) {
            sql.append(" AND p.payment_date >= ?");
            args.add(LocalDate.parse(from));
        }
        if (to != null && !to.isBlank()) {
            sql.append(" AND p.payment_date <= ?");
            args.add(LocalDate.parse(to));
        }
        if (branchId != null) {
            sql.append(" AND p.branch_id = ?");
            args.add(branchId);
        }
        sql.append(" ORDER BY p.payment_date DESC, p.id DESC LIMIT ? OFFSET ?");
        args.add(size);
        args.add((page - 1) * size);
        return db.query(sql.toString(), args.toArray());
    }

    public long count(String q, String direction, String from, String to, Long branchId) {
        StringBuilder sql = new StringBuilder("""
                SELECT COUNT(*) AS n FROM payments p LEFT JOIN parties pt ON pt.id = p.party_id WHERE 1=1
                """);
        List<Object> args = new ArrayList<>();
        if (q != null && !q.isBlank()) {
            sql.append(" AND (p.doc_no LIKE ? OR pt.name LIKE ? OR p.reference_no LIKE ?)");
            String like = "%" + q.trim() + "%";
            args.add(like);
            args.add(like);
            args.add(like);
        }
        if (direction != null && !direction.isBlank()) {
            sql.append(" AND p.direction = ?");
            args.add(direction);
        }
        if (from != null && !from.isBlank()) {
            sql.append(" AND p.payment_date >= ?");
            args.add(LocalDate.parse(from));
        }
        if (to != null && !to.isBlank()) {
            sql.append(" AND p.payment_date <= ?");
            args.add(LocalDate.parse(to));
        }
        if (branchId != null) {
            sql.append(" AND p.branch_id = ?");
            args.add(branchId);
        }
        return db.count(sql.toString(), args.toArray());
    }

    public Map<String, Object> find(long id) {
        Map<String, Object> p = db.queryOne("""
                SELECT p.*, pt.name AS party_name, b.name AS branch_name,
                       s.doc_no AS sale_doc_no, s.total_net, pu.doc_no AS purchase_doc_no
                FROM payments p LEFT JOIN parties pt ON pt.id = p.party_id
                JOIN branches b ON b.id = p.branch_id
                LEFT JOIN payment_allocations pa ON pa.payment_id = p.id
                LEFT JOIN sales s ON s.id = pa.sale_id
                LEFT JOIN purchases pu ON pu.id = pa.purchase_id
                WHERE p.id = ?
                """, id);
        if (p == null) {
            throw ApiException.notFound("Pembayaran tidak ditemukan.");
        }
        return p;
    }

    public long insert(Db.Tx tx, String docNo, long branchId, String direction, Long partyId, LocalDate date,
                       BigDecimal amount, String method, String reference, String sourceType, Long sourceId,
                       String paymentType, String notes, long userId) {
        return tx.insert("""
                INSERT INTO payments (doc_no, branch_id, direction, party_id, payment_date, amount, method,
                reference_no, source_type, source_id, payment_type, notes, created_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, docNo, branchId, direction, partyId, date, Money.nz(amount), method, reference,
                sourceType, sourceId, paymentType, notes, userId);
    }

    public void allocate(Db.Tx tx, long paymentId, Long saleId, Long purchaseId, BigDecimal amount) {
        tx.insert("""
                INSERT INTO payment_allocations (payment_id, sale_id, purchase_id, amount) VALUES (?, ?, ?, ?)
                """, paymentId, saleId, purchaseId, Money.nz(amount));
    }

    public BigDecimal paidForSale(long saleId) {
        return Db.dec(db.queryOne("""
                SELECT COALESCE(SUM(pa.amount),0) AS paid FROM payment_allocations pa
                JOIN payments p ON p.id = pa.payment_id AND p.status = 'POSTED' WHERE pa.sale_id = ?
                """, saleId), "paid");
    }

    public BigDecimal paidForPurchase(long purchaseId) {
        return Db.dec(db.queryOne("""
                SELECT COALESCE(SUM(pa.amount),0) AS paid FROM payment_allocations pa
                JOIN payments p ON p.id = pa.payment_id AND p.status = 'POSTED' WHERE pa.purchase_id = ?
                """, purchaseId), "paid");
    }

    public BigDecimal unallocatedAdvance(Db.Tx tx, long saleId) {
        return Db.dec(tx.queryOne("""
                SELECT COALESCE(SUM(p.amount),0) AS amount FROM payments p
                WHERE p.source_type = 'ADVANCE_CUSTOMER' AND p.source_id = ? AND p.status = 'POSTED'
                  AND NOT EXISTS (SELECT 1 FROM payment_allocations pa WHERE pa.payment_id = p.id)
                """, saleId), "amount");
    }

    public List<Map<String, Object>> receivables(Long branchId) {
        return db.query("""
                SELECT s.id, s.doc_no, s.sale_date, s.payment_status, s.total_net,
                       pt.name AS party_name,
                       COALESCE((SELECT SUM(pa.amount) FROM payment_allocations pa
                         JOIN payments p ON p.id = pa.payment_id AND p.status='POSTED'
                         WHERE pa.sale_id = s.id),0) AS paid,
                       s.total_net - COALESCE((SELECT SUM(pa.amount) FROM payment_allocations pa
                         JOIN payments p ON p.id = pa.payment_id AND p.status='POSTED'
                         WHERE pa.sale_id = s.id),0) AS remaining,
                       DATEDIFF(CURDATE(), s.sale_date) AS age_days
                FROM sales s JOIN parties pt ON pt.id = s.customer_id
                WHERE s.status IN ('DIKONFIRMASI','SELESAI')
                  AND (? IS NULL OR s.branch_id = ?)
                HAVING remaining > 0 ORDER BY s.sale_date
                """, branchId, branchId);
    }

    public List<Map<String, Object>> payables(Long branchId) {
        return db.query("""
                SELECT p.id, p.doc_no, p.purchase_date, p.due_date, pt.name AS party_name,
                       COALESCE((SELECT SUM(ri.acquisition_cost) FROM receipt_items ri
                         JOIN receipts r ON r.id = ri.receipt_id WHERE r.purchase_id = p.id),0) AS received_value,
                       COALESCE((SELECT SUM(pa.amount) FROM payment_allocations pa
                         JOIN payments pay ON pay.id = pa.payment_id AND pay.status='POSTED'
                         WHERE pa.purchase_id = p.id),0) AS paid,
                       COALESCE((SELECT SUM(ri.acquisition_cost) FROM receipt_items ri
                         JOIN receipts r ON r.id = ri.receipt_id WHERE r.purchase_id = p.id),0)
                       - COALESCE((SELECT SUM(pa.amount) FROM payment_allocations pa
                         JOIN payments pay ON pay.id = pa.payment_id AND pay.status='POSTED'
                         WHERE pa.purchase_id = p.id),0) AS remaining,
                       TIMESTAMPDIFF(DAY, COALESCE(p.due_date, p.purchase_date), CURDATE()) AS age_days
                FROM purchases p JOIN parties pt ON pt.id = p.supplier_id
                WHERE p.status IN ('DITERIMA_SEBAGIAN','SELESAI')
                  AND (? IS NULL OR p.branch_id = ?)
                HAVING remaining > 0 ORDER BY p.purchase_date
                """, branchId, branchId);
    }
}
