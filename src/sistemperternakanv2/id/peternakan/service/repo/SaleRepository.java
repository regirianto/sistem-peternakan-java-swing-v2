package id.peternakan.service.repo;

import id.peternakan.domain.Money;
import id.peternakan.service.api.ApiException;
import id.peternakan.service.db.Db;
import id.peternakan.service.support.Settings;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class SaleRepository {
    private final Db db;

    public SaleRepository(Db db) {
        this.db = db;
    }

    public List<Map<String, Object>> list(String q, String status, int page, int size, Long branchId) {
        StringBuilder sql = new StringBuilder("""
                SELECT s.id, s.doc_no, s.sale_date, s.status, s.payment_status, s.sale_type, s.total_net,
                       s.total_hpp, pt.name AS customer_name, pt.id AS customer_id, b.name AS branch_name,
                       (SELECT COUNT(*) FROM sale_items si WHERE si.sale_id = s.id) AS item_count,
                       s.total_net - COALESCE((SELECT SUM(pa.amount) FROM payment_allocations pa
                         JOIN payments pay ON pay.id = pa.payment_id AND pay.status = 'POSTED'
                         WHERE pa.sale_id = s.id),0) AS remaining
                FROM sales s JOIN parties pt ON pt.id = s.customer_id
                JOIN branches b ON b.id = s.branch_id WHERE 1=1
                """);
        List<Object> args = new ArrayList<>();
        if (q != null && !q.isBlank()) {
            sql.append(" AND (s.doc_no LIKE ? OR pt.name LIKE ?)");
            String like = "%" + q.trim() + "%";
            args.add(like);
            args.add(like);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND s.status = ?");
            args.add(status);
        }
        if (branchId != null) {
            sql.append(" AND s.branch_id = ?");
            args.add(branchId);
        }
        sql.append(" ORDER BY s.sale_date DESC, s.id DESC LIMIT ? OFFSET ?");
        args.add(size);
        args.add((page - 1) * size);
        return db.query(sql.toString(), args.toArray());
    }

    public long count(String q, String status, Long branchId) {
        StringBuilder sql = new StringBuilder("""
                SELECT COUNT(*) AS n FROM sales s JOIN parties pt ON pt.id = s.customer_id WHERE 1=1
                """);
        List<Object> args = new ArrayList<>();
        if (q != null && !q.isBlank()) {
            sql.append(" AND (s.doc_no LIKE ? OR pt.name LIKE ?)");
            String like = "%" + q.trim() + "%";
            args.add(like);
            args.add(like);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND s.status = ?");
            args.add(status);
        }
        if (branchId != null) {
            sql.append(" AND s.branch_id = ?");
            args.add(branchId);
        }
        return db.count(sql.toString(), args.toArray());
    }

    public Map<String, Object> find(long id) {
        Map<String, Object> sale = db.queryOne("""
                SELECT s.*, pt.name AS customer_name, pt.phone AS customer_phone, u.full_name AS created_by_name,
                       b.name AS branch_name,
                       ap.full_name AS approved_by_name,
                       (SELECT COALESCE(SUM(pa.amount),0) FROM payment_allocations pa
                        JOIN payments pay ON pay.id = pa.payment_id AND pay.status = 'POSTED'
                        WHERE pa.sale_id = s.id) AS paid_value,
                       (SELECT COALESCE(SUM(pay.amount),0) FROM payments pay
                        WHERE pay.source_type = 'ADVANCE_CUSTOMER' AND pay.source_id = s.id AND pay.status = 'POSTED'
                          AND NOT EXISTS (SELECT 1 FROM payment_allocations pa WHERE pa.payment_id = pay.id)) AS advance_value
                FROM sales s JOIN parties pt ON pt.id = s.customer_id
                JOIN branches b ON b.id = s.branch_id
                LEFT JOIN users u ON u.id = s.created_by
                LEFT JOIN users ap ON ap.id = s.approved_by
                WHERE s.id = ?
                """, id);
        if (sale == null) {
            throw ApiException.notFound("Penjualan tidak ditemukan.");
        }
        sale.put("items", items(id));
        sale.put("remaining", Money.nz(Db.dec(sale, "total_net").subtract(Db.dec(sale, "paid_value"))));
        sale.put("payments", db.query("""
                SELECT p.id, p.doc_no, p.payment_date, p.amount, p.method, p.status, p.payment_type
                FROM payments p JOIN payment_allocations pa ON pa.payment_id = p.id
                WHERE pa.sale_id = ? ORDER BY p.payment_date, p.id
                """, id));
        sale.put("approvals", db.query("""
                SELECT sa.*, req.full_name AS requested_by_name, app.full_name AS approved_by_name
                FROM sale_approvals sa LEFT JOIN users req ON req.id = sa.requested_by
                LEFT JOIN users app ON app.id = sa.approved_by
                WHERE sa.sale_id = ? ORDER BY sa.id DESC
                """, id));
        return sale;
    }

    public List<Map<String, Object>> items(long saleId) {
        return db.query("""
                SELECT si.*, g.code AS goat_code, g.jenis, g.gender, g.acquisition_cost,
                       mb.batch_no, mb.hpp_per_kg AS batch_hpp, mg.code AS meat_goat_code,
                       pen.name AS pen_name
                FROM sale_items si
                LEFT JOIN goats g ON g.id = si.goat_id
                LEFT JOIN meat_batches mb ON mb.id = si.meat_batch_id
                LEFT JOIN goats mg ON mg.id = mb.goat_id
                LEFT JOIN pen_placements pp ON pp.goat_id = g.id AND pp.exited_at IS NULL
                LEFT JOIN pens pen ON pen.id = pp.pen_id
                WHERE si.sale_id = ? ORDER BY si.line_no
                """, saleId);
    }

    /** Simpan/ubah draft. Total dihitung ulang di server. */
    public long saveDraft(Db.Tx tx, Long saleId, String docNo, long branchId, long customerId, LocalDate saleDate,
                          String notes, List<Map<String, Object>> items, long userId) {
        if (items == null || items.isEmpty()) {
            throw ApiException.badRequest("Pilih minimal satu kambing atau batch daging untuk dijual.");
        }
        boolean hasGoat = false;
        boolean hasMeat = false;
        for (Map<String, Object> item : items) {
            if ("DAGING".equals(String.valueOf(item.getOrDefault("itemType", "KAMBING")))) {
                hasMeat = true;
            } else {
                hasGoat = true;
            }
        }
        if (hasGoat && hasMeat) {
            throw ApiException.badRequest("Satu dokumen penjualan tidak boleh mencampur kambing hidup dan daging.");
        }
        String saleType = hasMeat ? "DAGING" : "KAMBING";
        BigDecimal meatPrice = Settings.getDecimal(db, "meat.price_per_kg", Money.ZERO);

        List<Map<String, Object>> lines = new ArrayList<>();
        BigDecimal totalGross = Money.ZERO;
        Map<Long, BigDecimal> batchUsed = new java.util.HashMap<>();
        for (Map<String, Object> item : items) {
            Map<String, Object> row = new java.util.LinkedHashMap<>();
            if (hasMeat) {
                long batchId = Long.parseLong(String.valueOf(item.get("meatBatchId")));
                BigDecimal weight = item.get("weight") == null ? null
                        : Money.quantity(String.valueOf(item.get("weight")));
                if (weight == null || weight.signum() <= 0) {
                    throw ApiException.badRequest("Jumlah kilogram daging wajib diisi.");
                }
                if (meatPrice.signum() <= 0) {
                    throw ApiException.badRequest(
                            "Harga daging per kg belum diatur owner pada menu Persediaan Daging.");
                }
                Map<String, Object> batch = tx.queryOne("""
                        SELECT mb.*, g.code AS goat_code FROM meat_batches mb
                        JOIN goats g ON g.id = mb.goat_id WHERE mb.id = ? FOR UPDATE
                        """, batchId);
                if (batch == null) {
                    throw ApiException.notFound("Batch daging tidak ditemukan.");
                }
                if (Db.lng(batch, "branch_id") != branchId) {
                    throw ApiException.conflict("Batch daging berada di cabang lain.");
                }
                if (!"OPEN".equals(Db.str(batch, "status"))) {
                    throw ApiException.conflict("Batch daging " + Db.str(batch, "batch_no") + " sudah habis.");
                }
                BigDecimal used = batchUsed.getOrDefault(batchId, Money.ZERO);
                BigDecimal available = Db.dec(batch, "qty_remaining_kg").subtract(used);
                if (available.compareTo(weight) < 0) {
                    throw ApiException.conflict("Sisa batch " + Db.str(batch, "batch_no") + " hanya "
                            + available.stripTrailingZeros().toPlainString() + " kg.");
                }
                batchUsed.put(batchId, used.add(weight));
                BigDecimal rate = Money.nz(meatPrice);
                BigDecimal gross = Money.multiply(weight, rate);
                row.put("itemType", "DAGING");
                row.put("goatId", null);
                row.put("meatBatchId", batchId);
                row.put("method", "PER_KG");
                row.put("weight", weight);
                row.put("rate", rate);
                row.put("gross", gross);
                row.put("hpp", Money.multiply(weight, Db.dec(batch, "hpp_per_kg")));
            } else {
                long goatId = Long.parseLong(String.valueOf(item.get("goatId")));
                Map<String, Object> goat = tx.queryOne("""
                        SELECT g.*, (SELECT w.weight_kg FROM weight_records w WHERE w.goat_id = g.id
                                     ORDER BY w.measured_at DESC, w.id DESC LIMIT 1) AS latest_weight
                        FROM goats g WHERE g.id = ?
                        """, goatId);
                if (goat == null) {
                    throw ApiException.notFound("Kambing tidak ditemukan.");
                }
                if (Db.lng(goat, "branch_id") != branchId) {
                    throw ApiException.conflict("Kambing " + Db.str(goat, "code") + " berada di cabang lain.");
                }
                if (!"AKTIF".equals(Db.str(goat, "status"))) {
                    throw ApiException.conflict("Kambing " + Db.str(goat, "code") + " tidak aktif / sudah terjual.");
                }
                if (!"SIAP_JUAL".equals(Db.str(goat, "sale_readiness"))) {
                    throw ApiException.conflict("Kambing " + Db.str(goat, "code") + " belum layak jual (karantina).");
                }
                if (tx.queryOne("SELECT 1 AS x FROM goat_sale_locks WHERE goat_id = ?", goatId) != null) {
                    throw ApiException.conflict("Kambing " + Db.str(goat, "code") + " sudah dikonfirmasi pada penjualan lain.");
                }
                String method = String.valueOf(item.getOrDefault("method", "PER_EKOR"));
                BigDecimal rate = Money.of(String.valueOf(item.get("rate")));
                BigDecimal weight = item.get("weight") == null ? null : Money.quantity(String.valueOf(item.get("weight")));
                BigDecimal gross;
                if ("PER_KG".equals(method)) {
                    if (weight == null || weight.signum() <= 0) {
                        throw ApiException.badRequest("Metode per kg wajib menyimpan berat jual aktual.");
                    }
                    gross = Money.multiply(weight, rate);
                } else {
                    method = "PER_EKOR";
                    gross = Money.nz(rate);
                }
                if (gross.signum() <= 0) {
                    throw ApiException.badRequest("Harga jual harus lebih dari 0.");
                }
                row.put("itemType", "KAMBING");
                row.put("goatId", goatId);
                row.put("meatBatchId", null);
                row.put("method", method);
                row.put("weight", weight);
                row.put("rate", rate);
                row.put("gross", gross);
                row.put("hpp", Db.dec(goat, "acquisition_cost"));
            }
            lines.add(row);
            totalGross = totalGross.add((BigDecimal) row.get("gross"));
        }

        for (Map<String, Object> g : lines) {
            g.put("net", g.get("gross"));
        }

        BigDecimal totalNet = lines.stream().map(g -> (BigDecimal) g.get("net"))
                .reduce(Money.ZERO, BigDecimal::add);
        BigDecimal totalHpp = lines.stream().map(g -> (BigDecimal) g.get("hpp"))
                .reduce(Money.ZERO, BigDecimal::add);

        long id;
        if (saleId == null) {
            id = tx.insert("""
                    INSERT INTO sales (doc_no, branch_id, customer_id, sale_date, status, sale_type, total_gross,
                    total_net, total_hpp, notes, created_by)
                    VALUES (?, ?, ?, ?, 'DRAFT', ?, ?, ?, ?, ?, ?)
                    """, docNo, branchId, customerId, saleDate, saleType, totalGross, totalNet, totalHpp,
                    notes, userId);
        } else {
            Map<String, Object> existing = tx.queryOne("SELECT status, branch_id FROM sales WHERE id = ? FOR UPDATE",
                    saleId);
            if (existing == null) {
                throw ApiException.notFound("Penjualan tidak ditemukan.");
            }
            if (Db.lng(existing, "branch_id") != branchId) {
                throw ApiException.badRequest("Cabang penjualan tidak dapat diubah.");
            }
            if (!"DRAFT".equals(Db.str(existing, "status"))) {
                throw ApiException.conflict("Hanya draft yang dapat diubah.");
            }
            tx.update("""
                    UPDATE sales SET customer_id = ?, sale_date = ?, sale_type = ?, total_gross = ?,
                    total_net = ?, total_hpp = ?, notes = ?, version = version + 1,
                    approved_by = NULL, approval_reason = NULL WHERE id = ?
                    """, customerId, saleDate, saleType, totalGross, totalNet, totalHpp, notes, saleId);
            tx.update("DELETE FROM sale_items WHERE sale_id = ?", saleId);
            id = saleId;
        }
        int line = 1;
        for (Map<String, Object> g : lines) {
            tx.update("""
                    INSERT INTO sale_items (sale_id, item_type, goat_id, meat_batch_id, line_no, pricing_method,
                    weight_kg, rate, gross, net, hpp)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, id, g.get("itemType"), g.get("goatId"), g.get("meatBatchId"), line++, g.get("method"),
                    g.get("weight"), g.get("rate"), g.get("gross"), g.get("net"), g.get("hpp"));
        }
        return id;
    }

    public Map<String, Object> lockSale(Db.Tx tx, long id) {
        Map<String, Object> sale = tx.queryOne("SELECT * FROM sales WHERE id = ? FOR UPDATE", id);
        if (sale == null) {
            throw ApiException.notFound("Penjualan tidak ditemukan.");
        }
        return sale;
    }

    public List<Map<String, Object>> lockItems(Db.Tx tx, long saleId) {
        return tx.query("""
                SELECT si.*, g.code, g.status, g.sale_readiness, g.acquisition_cost,
                       mb.qty_remaining_kg, mb.hpp_per_kg AS batch_hpp, mb.status AS batch_status,
                       mb.batch_no, mb.goat_id AS meat_goat_id
                FROM sale_items si
                LEFT JOIN goats g ON g.id = si.goat_id
                LEFT JOIN meat_batches mb ON mb.id = si.meat_batch_id
                WHERE si.sale_id = ? ORDER BY si.line_no FOR UPDATE
                """, saleId);
    }

    public void markSold(Db.Tx tx, long goatId, LocalDate saleDate, long saleId) {
        int updated = tx.update("""
                UPDATE goats SET status = 'TERJUAL', version = version + 1
                WHERE id = ? AND status = 'AKTIF' AND sale_readiness = 'SIAP_JUAL'
                """, goatId);
        if (updated != 1) {
            throw ApiException.conflict("Kambing berubah status saat konfirmasi. Muat ulang data.");
        }
        tx.insert("INSERT INTO goat_sale_locks (goat_id, sale_id) VALUES (?, ?)", goatId, saleId);
        tx.update("""
                UPDATE pen_placements SET exited_at = ?
                WHERE goat_id = ? AND exited_at IS NULL
                """, saleDate.atTime(23, 59, 59), goatId);
    }

    public void lockGoat(Db.Tx tx, long goatId, long saleId) {
        tx.insert("INSERT INTO goat_sale_locks (goat_id, sale_id) VALUES (?, ?)", goatId, saleId);
    }

    public void releaseLock(Db.Tx tx, long goatId) {
        tx.update("DELETE FROM goat_sale_locks WHERE goat_id = ?", goatId);
    }

    public void updateTotals(Db.Tx tx, long saleId) {
        Map<String, Object> row = tx.queryOne("""
                SELECT COALESCE(SUM(gross),0) gross, COALESCE(SUM(net),0) net,
                       COALESCE(SUM(hpp),0) hpp
                FROM sale_items WHERE sale_id = ?
                """, saleId);
        tx.update("""
                UPDATE sales SET total_gross = ?, total_net = ?, total_hpp = ? WHERE id = ?
                """, Db.dec(row, "gross"), Db.dec(row, "net"), Db.dec(row, "hpp"), saleId);
    }

    public void setStatus(Db.Tx tx, long saleId, String status) {
        tx.update("UPDATE sales SET status = ? WHERE id = ?", status, saleId);
        if ("DIKONFIRMASI".equals(status) || "SELESAI".equals(status)) {
            tx.update("UPDATE sales SET confirmed_at = COALESCE(confirmed_at, NOW()) WHERE id = ?", saleId);
        }
    }

    public void refreshPaymentStatus(Db.Tx tx, long saleId) {
        Map<String, Object> sale = tx.queryOne("SELECT total_net, status FROM sales WHERE id = ? FOR UPDATE", saleId);
        BigDecimal paid = Db.dec(tx.queryOne("""
                SELECT COALESCE(SUM(pa.amount),0) AS paid FROM payment_allocations pa
                JOIN payments p ON p.id = pa.payment_id AND p.status = 'POSTED'
                WHERE pa.sale_id = ?
                """, saleId), "paid");
        BigDecimal net = Db.dec(sale, "total_net");
        String paymentStatus = paid.signum() == 0 ? "BELUM_BAYAR"
                : paid.compareTo(net) >= 0 ? "LUNAS" : "SEBAGIAN";
        String status = Db.str(sale, "status");
        if ("DIKONFIRMASI".equals(status) && "LUNAS".equals(paymentStatus)) {
            status = "SELESAI";
        } else if ("SELESAI".equals(status) && !"LUNAS".equals(paymentStatus)) {
            status = "DIKONFIRMASI";
        }
        tx.update("UPDATE sales SET payment_status = ?, status = ? WHERE id = ?", paymentStatus, status, saleId);
    }

    public List<Map<String, Object>> pendingApprovals(Long branchId) {
        return db.query("""
                SELECT s.id, s.doc_no, s.sale_date, s.total_net, pt.name AS customer_name,
                       b.name AS branch_name, sa.reason, sa.approved_at
                FROM sales s JOIN parties pt ON pt.id = s.customer_id
                JOIN branches b ON b.id = s.branch_id
                JOIN sale_approvals sa ON sa.sale_id = s.id
                WHERE s.status = 'DRAFT' AND s.approved_by IS NULL AND sa.approved_at IS NULL
                  AND (? IS NULL OR s.branch_id = ?)
                ORDER BY s.id DESC
                """, branchId, branchId);
    }
}
