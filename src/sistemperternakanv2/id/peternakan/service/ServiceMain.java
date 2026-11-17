package id.peternakan.service;

import id.peternakan.service.api.ApiServer;
import id.peternakan.service.config.AppConfig;
import id.peternakan.service.db.Db;
import id.peternakan.service.db.Migrations;
import id.peternakan.service.security.AuthService;
import id.peternakan.service.security.SessionInfo;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Titik masuk layanan aplikasi.
 *
 * Perintah:
 *   serve                      Menjalankan API (default)
 *   migrate                    Menerapkan migrasi database
 *   bootstrap-owner            Membuat akun owner pertama
 *   demo                       Memuat data contoh (bukan data produksi)
 *   create-user                Membuat pengguna baru
 *   status                     Menampilkan status instalasi
 */
public final class ServiceMain {

    public static void main(String[] args) throws Exception {
        String command = args.length == 0 ? "serve" : args[0];
        Map<String, String> options = parse(args);
        AppConfig config = new AppConfig();

        switch (command) {
            case "serve" -> serve(config, options);
            case "migrate" -> {
                try (Db db = new Db(config)) {
                    Migrations.run(db);
                    System.out.println("Migrasi selesai. Versi skema: " + Migrations.currentVersion(db));
                }
            }
            case "bootstrap-owner" -> {
                String username = required(options, "username");
                String password = required(options, "password");
                String name = options.getOrDefault("name", "Owner");
                try (Db db = new Db(config)) {
                    Migrations.run(db);
                    AuthService auth = new AuthService(db);
                    Map<String, Object> result = auth.bootstrapOwner(username, password, name);
                    System.out.println("Owner dibuat: " + result.get("username") + " (id " + result.get("id") + ")");
                }
            }
            case "create-user" -> {
                String username = required(options, "username");
                String password = required(options, "password");
                String name = options.getOrDefault("name", username);
                String role = options.getOrDefault("role", "PETUGAS");
                try (Db db = new Db(config)) {
                    Migrations.run(db);
                    AuthService auth = new AuthService(db);
                    SessionInfo owner = auth.systemSession(options.getOrDefault("owner", "owner"));
                    Map<String, Object> result = auth.createUser(owner, username, password, name, role);
                    System.out.println("Pengguna dibuat: " + result.get("username") + " role " + role);
                }
            }
            case "demo" -> {
                try (Db db = new Db(config)) {
                    Migrations.run(db);
                    AuthService auth = new AuthService(db);
                    SessionInfo owner = auth.systemSession(options.getOrDefault("owner", "owner"));
                    DemoData demo = new DemoData(db, config, auth);
                    Map<String, Object> summary = demo.load(owner);
                    System.out.println("Data contoh dimuat:");
                    summary.forEach((key, value) -> System.out.println("  " + key + ": " + value));
                }
            }
            case "status" -> {
                try (Db db = new Db(config)) {
                    AuthService auth = new AuthService(db);
                    System.out.println("Versi skema: " + Migrations.currentVersion(db));
                    System.out.println("Bootstrap diperlukan: " + auth.bootstrapStatus().get("needsBootstrap"));
                }
            }
            default -> {
                System.err.println("Perintah tidak dikenal: " + command);
                System.err.println("Gunakan: serve | migrate | bootstrap-owner | demo | create-user | status");
                System.exit(2);
            }
        }
    }

    private static void serve(AppConfig config, Map<String, String> options) throws Exception {
        int port = options.containsKey("port") ? Integer.parseInt(options.get("port")) : config.port();
        try (Db db = new Db(config)) {
            Migrations.run(db);
            ApiServer server = new ApiServer(db, config);
            server.start(port);
            System.out.println("Layanan peternakan berjalan di http://localhost:" + port);
            System.out.println("Tekan Ctrl+C untuk berhenti.");
            Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
            Thread.currentThread().join();
        }
    }

    private static Map<String, String> parse(String[] args) {
        Map<String, String> options = new LinkedHashMap<>();
        for (int i = 1; i < args.length; i++) {
            String arg = args[i];
            if (arg.startsWith("--")) {
                String key = arg.substring(2);
                if (key.contains("=")) {
                    int idx = key.indexOf('=');
                    options.put(key.substring(0, idx), key.substring(idx + 1));
                } else if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                    options.put(key, args[++i]);
                } else {
                    options.put(key, "true");
                }
            }
        }
        return options;
    }

    private static String required(Map<String, String> options, String key) {
        String value = options.get(key);
        if (value == null || value.isBlank()) {
            System.err.println("Opsi --" + key + " wajib diisi.");
            System.exit(2);
        }
        return value;
    }

    private ServiceMain() {
    }
}
