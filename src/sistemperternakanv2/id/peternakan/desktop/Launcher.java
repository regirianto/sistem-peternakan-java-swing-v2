package id.peternakan.desktop;

import id.peternakan.service.AutoSeed;
import id.peternakan.service.api.ApiServer;
import id.peternakan.service.config.AppConfig;
import id.peternakan.service.db.Db;
import id.peternakan.service.db.Migrations;

/**
 * Entry point aplikasi desktop + layanan API (project NetBeans Ant).
 *
 * Klik Run di NetBeans sekarang melakukan dua hal:
 *  1. menjalankan layanan API internal (migrasi DB + HTTP server 8765) di thread latar;
 *  2. membuka klien Swing desktop.
 *
 * Dependency berupa jar di src/sistemperternakanv2/lib; classpath diatur di
 * nbproject/project.properties.
 */
public final class Launcher {

    public static void main(String[] args) {
        AppConfig config = new AppConfig();
        boolean[] ok = new boolean[]{false};

        // 1) Layanan API di thread terpisah (bukan EDT). Db tetap terbuka sepanjang
        //    program berjalan dan ditutup lewat shutdown hook.
        Thread service = new Thread(() -> {
            try {
                Db db = new Db(config);
                Migrations.run(db);
                try {
                    AutoSeed.run(db, config);
                } catch (Throwable seedError) {
                    System.err.println("[auto-seed] Gagal memuat data contoh: " + seedError.getMessage());
                    seedError.printStackTrace();
                }
                ApiServer server = new ApiServer(db, config);
                server.start(config.port());
                ok[0] = true;
                System.out.println("Layanan peternakan berjalan di http://localhost:" + config.port());
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    server.stop();
                    db.close();
                }));
            } catch (Throwable t) {
                System.err.println("Gagal menjalankan layanan: " + t.getMessage());
                t.printStackTrace();
            }
        }, "service-api");
        service.setDaemon(true);
        service.start();

        waitForService(config.port());

        // 2) Klien Swing (memicu munculnya jendela login). Jika layanan dan
        //    database tidak hidup, tampilkan pesan jelas alih-alih login gagal.
        javax.swing.SwingUtilities.invokeLater(() -> {
            if (ok[0]) {
                id.peternakan.desktop.DesktopApp.main(args);
            } else if (!java.awt.GraphicsEnvironment.isHeadless()) {
                javax.swing.JOptionPane.showMessageDialog(null,
                        "MySQL tidak dapat dihubungi.\n"
                        + "Periksa service.properties (db.url/db.user/db.password) dan jalankan server database.",
                        "Layanan Gagal Start", javax.swing.JOptionPane.ERROR_MESSAGE);
            } else {
                System.err.println("Layanan/database tidak hidup; klien Swing tidak dibuka.");
            }
        });
    }

    /** Tunggu sampai layanan merespons sehingga login tidak gagal karena sekali koneksi. */
    private static void waitForService(int port) {
        java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create("http://localhost:" + port + "/api/health"))
                .GET()
                .build();
        for (int attempt = 0; attempt < 30; attempt++) {
            try {
                client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
                return;
            } catch (Exception e) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
        System.err.println("Layanan tidak merespons di port " + port + " (cek database).");
    }

    private Launcher() {
    }
}
