package id.peternakan.service.repo;

import id.peternakan.service.api.ApiException;
import id.peternakan.service.db.Db;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class PartyRepository {
    private final Db db;

    public PartyRepository(Db db) {
        this.db = db;
    }

    public List<Map<String, Object>> list(String role, String q, int page, int size) {
        StringBuilder sql = new StringBuilder("""
                SELECT p.id, p.code, p.name, p.phone, p.address, p.active, p.notes,
                       GROUP_CONCAT(pr.role_code ORDER BY pr.role_code) AS roles
                FROM parties p JOIN party_roles pr ON pr.party_id = p.id
                WHERE 1=1
                """);
        List<Object> args = new ArrayList<>();
        if (role != null && !role.isBlank()) {
            sql.append(" AND pr.role_code = ?");
            args.add(role);
        }
        if (q != null && !q.isBlank()) {
            sql.append(" AND (p.name LIKE ? OR p.code LIKE ? OR p.phone LIKE ?)");
            String like = "%" + q.trim() + "%";
            args.add(like);
            args.add(like);
            args.add(like);
        }
        sql.append(" GROUP BY p.id ORDER BY p.name LIMIT ? OFFSET ?");
        args.add(size);
        args.add((page - 1) * size);
        return db.query(sql.toString(), args.toArray());
    }

    public long count(String role, String q) {
        StringBuilder sql = new StringBuilder("""
                SELECT COUNT(DISTINCT p.id) AS n FROM parties p JOIN party_roles pr ON pr.party_id = p.id WHERE 1=1
                """);
        List<Object> args = new ArrayList<>();
        if (role != null && !role.isBlank()) {
            sql.append(" AND pr.role_code = ?");
            args.add(role);
        }
        if (q != null && !q.isBlank()) {
            sql.append(" AND (p.name LIKE ? OR p.code LIKE ? OR p.phone LIKE ?)");
            String like = "%" + q.trim() + "%";
            args.add(like);
            args.add(like);
            args.add(like);
        }
        return db.count(sql.toString(), args.toArray());
    }

    public Map<String, Object> find(long id) {
        Map<String, Object> party = db.queryOne("SELECT * FROM parties WHERE id = ?", id);
        if (party == null) {
            throw ApiException.notFound("Pihak tidak ditemukan.");
        }
        party.put("roles", db.query("SELECT role_code FROM party_roles WHERE party_id = ?", id).stream()
                .map(r -> Db.str(r, "role_code")).toList());
        return party;
    }

    public long create(Db.Tx tx, String code, String name, String phone, String address,
                       boolean active, String notes, List<String> roles) {
        if (name == null || name.isBlank()) {
            throw ApiException.badRequest("Nama pihak wajib diisi.");
        }
        if (roles == null || roles.isEmpty()) {
            throw ApiException.badRequest("Pilih minimal satu peran: SUPPLIER atau CUSTOMER.");
        }
        String finalCode = code == null || code.isBlank() ? nextCode(tx, roles) : code.trim();
        long id = tx.insert("""
                INSERT INTO parties (code, name, phone, address, active, notes) VALUES (?, ?, ?, ?, ?, ?)
                """, finalCode, name.trim(), phone, address, active ? 1 : 0, notes);
        for (String role : roles) {
            tx.update("INSERT INTO party_roles (party_id, role_code) VALUES (?, ?)", id, role);
        }
        return id;
    }

    public void update(Db.Tx tx, long id, String name, String phone, String address, boolean active, String notes,
                       List<String> roles) {
        if (name == null || name.isBlank()) {
            throw ApiException.badRequest("Nama pihak wajib diisi.");
        }
        tx.update("UPDATE parties SET name = ?, phone = ?, address = ?, active = ?, notes = ? WHERE id = ?",
                name.trim(), phone, address, active ? 1 : 0, notes, id);
        if (roles != null) {
            tx.update("DELETE FROM party_roles WHERE party_id = ?", id);
            for (String role : roles) {
                tx.update("INSERT INTO party_roles (party_id, role_code) VALUES (?, ?)", id, role);
            }
        }
    }

    public boolean hasTransactions(Db.Tx tx, long id) {
        return Db.lng(tx.queryOne("""
                SELECT (SELECT COUNT(*) FROM purchases WHERE supplier_id = ?) +
                       (SELECT COUNT(*) FROM sales WHERE customer_id = ?) +
                       (SELECT COUNT(*) FROM payments WHERE party_id = ?) AS n
                """, id, id, id), "n") > 0;
    }

    private String nextCode(Db.Tx tx, List<String> roles) {
        String prefix = roles.contains("SUPPLIER") && !roles.contains("CUSTOMER") ? "SUP"
                : roles.contains("CUSTOMER") && !roles.contains("SUPPLIER") ? "CUS" : "PTY";
        Map<String, Object> row = tx.queryOne("SELECT COUNT(*) + 1 AS n FROM parties WHERE code LIKE ?", prefix + "-%");
        int n = row == null ? 1 : ((Number) row.get("n")).intValue();
        String code = "%s-%04d".formatted(prefix, n);
        while (tx.queryOne("SELECT id FROM parties WHERE code = ?", code) != null) {
            n++;
            code = "%s-%04d".formatted(prefix, n);
        }
        return code;
    }

    public List<Map<String, Object>> purchases(long partyId) {
        return db.query("""
                SELECT id, doc_no, purchase_date, status, total_amount FROM purchases
                WHERE supplier_id = ? ORDER BY purchase_date DESC LIMIT 100
                """, partyId);
    }

    public List<Map<String, Object>> sales(long partyId) {
        return db.query("""
                SELECT id, doc_no, sale_date, status, payment_status, total_net FROM sales
                WHERE customer_id = ? ORDER BY sale_date DESC LIMIT 100
                """, partyId);
    }
}
