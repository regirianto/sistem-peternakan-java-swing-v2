package id.peternakan.service;

import id.peternakan.service.config.AppConfig;
import id.peternakan.service.db.Db;
import id.peternakan.service.security.AuthService;
import id.peternakan.service.security.SessionInfo;

import java.util.Map;

/**
 * Mengisi data contoh saat aplikasi desktop pertama kali dijalankan pada
 * database yang masih kosong. Dapat dimatikan dengan {@code demo.auto_seed=false}.
 *
 * Hanya berjalan bila tidak ada data operasional sama sekali, sehingga data
 * yang sudah dipakai tidak akan tercampur.
 */
public final class AutoSeed {
    private AutoSeed() {
    }

    public static void run(Db db, AppConfig config) {
        if (!config.autoSeedDemo()) {
            return;
        }
        Map<String, Object> business = db.queryOne("""
                SELECT (SELECT COUNT(*) FROM goats)
                     + (SELECT COUNT(*) FROM purchases)
                     + (SELECT COUNT(*) FROM receipts)
                     + (SELECT COUNT(*) FROM sales)
                     + (SELECT COUNT(*) FROM parties)
                     + (SELECT COUNT(*) FROM feed_items) AS total
                """);
        long existing = business == null ? 0 : ((Number) business.get("total")).longValue();
        if (existing > 0) {
            System.out.println("[auto-seed] Database sudah memiliki data; data contoh dilewati.");
            return;
        }

        AuthService auth = new AuthService(db);
        Map<String, Object> owner = db.queryOne("""
                SELECT u.username FROM users u JOIN roles r ON r.id = u.role_id
                WHERE r.code = 'OWNER' AND u.active = 1 ORDER BY u.id LIMIT 1
                """);
        String username;
        if (owner == null) {
            auth.bootstrapOwner("owner", "Owner#2024", "Owner");
            username = "owner";
            System.out.println("[auto-seed] Owner awal dibuat: owner / Owner#2024");
        } else {
            username = Db.str(owner, "username");
        }

        SessionInfo session = auth.systemSession(username);
        DemoData demo = new DemoData(db, config, auth);
        Map<String, Object> summary = demo.load(session);
        System.out.println("[auto-seed] Data contoh dimuat:");
        summary.forEach((key, value) -> System.out.println("  " + key + ": " + value));
    }
}
