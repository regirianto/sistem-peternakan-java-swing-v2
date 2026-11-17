package id.peternakan.service.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Konfigurasi layanan. Rahasia tidak pernah ditulis ke source control:
 * nilai diambil dari environment atau file service.properties di luar repo.
 */
public final class AppConfig {
    private final Properties props = new Properties();

    public AppConfig() {
        String configPath = System.getenv().getOrDefault("SERVICE_CONFIG", "service.properties");
        Path path = Path.of(configPath);
        if (!Files.isRegularFile(path)) {
            // fallback: jalankan dari modul (mis. run NetBeans) dengan
            // konfigurasi di folder root proyek.
            path = Path.of("..", configPath);
        }
        if (Files.isRegularFile(path)) {
            try (InputStream in = Files.newInputStream(path)) {
                props.load(in);
            } catch (IOException e) {
                throw new IllegalStateException("Gagal membaca konfigurasi: " + path, e);
            }
        }
    }

    public String get(String key, String fallback) {
        String envKey = key.toUpperCase().replace('.', '_');
        String env = System.getenv(envKey);
        if (env != null && !env.isBlank()) {
            return env;
        }
        return props.getProperty(key, fallback);
    }

    public int getInt(String key, int fallback) {
        String v = get(key, null);
        return v == null ? fallback : Integer.parseInt(v);
    }

    public String dbUrl() {
        return get("db.url", "jdbc:mysql://localhost:3306/peternakan?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Jakarta&characterEncoding=utf8");
    }

    public String dbUser() {
        return get("db.user", "peternakan");
    }

    public String dbPassword() {
        return get("db.password", "");
    }

    public int port() {
        return getInt("server.port", 8765);
    }

    public boolean autoSeedDemo() {
        return Boolean.parseBoolean(get("demo.auto_seed", "true"));
    }

    public String attachmentDir() {
        return get("attachment.dir", "attachments");
    }

    public String backupDir() {
        return get("backup.dir", "backups");
    }

    public String mysqldump() {
        return get("mysqldump.bin", "mysqldump");
    }

    public String mysqlClient() {
        return get("mysql.bin", "mysql");
    }
}
