package id.peternakan.service.svc;

import id.peternakan.domain.Permissions;
import id.peternakan.service.api.ApiException;
import id.peternakan.service.config.AppConfig;
import id.peternakan.service.db.Db;
import id.peternakan.service.db.Migrations;
import id.peternakan.service.security.Audit;
import id.peternakan.service.security.SessionInfo;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public final class BackupService {
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final Db db;
    private final AppConfig config;

    public BackupService(Db db, AppConfig config) {
        this.db = db;
        this.config = config;
    }

    public Map<String, Object> list(SessionInfo session) {
        session.require(Permissions.BACKUP_MANAGE);
        return Map.of("items", db.query("""
                SELECT b.*, u.full_name AS created_by_name FROM backup_history b
                LEFT JOIN users u ON u.id = b.created_by ORDER BY b.id DESC LIMIT 100
                """));
    }

    public Map<String, Object> backup(SessionInfo session) {
        session.require(Permissions.BACKUP_MANAGE);
        if (!session.isOwner()) {
            throw ApiException.forbidden("Backup hanya dapat dilakukan owner.");
        }
        Path dir = Path.of(config.backupDir());
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new ApiException(500, "Gagal membuat folder backup: " + e.getMessage());
        }
        String fileName = "peternakan-" + LocalDateTime.now().format(STAMP) + ".sql";
        Path target = dir.resolve(fileName).toAbsolutePath();
        String database = databaseName();
        List<String> command = new ArrayList<>();
        command.add(config.mysqldump());
        command.add("--single-transaction");
        command.add("--routines");
        command.add("--triggers");
        command.add("--no-tablespaces");
        command.add("-h");
        command.add(host());
        command.add("-P");
        command.add(String.valueOf(port()));
        command.add("-u");
        command.add(db.user());
        command.add(database);
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.environment().put("MYSQL_PWD", db.password() == null ? "" : db.password());
        pb.redirectOutput(target.toFile());
        pb.redirectErrorStream(false);
        File errorFile = dir.resolve(fileName + ".err").toFile();
        pb.redirectError(errorFile);
        int exit = run(pb);
        String result = exit == 0 ? "SUCCESS" : "FAILED";
        long size = exit == 0 ? target.toFile().length() : 0;
        db.withTx(tx -> {
            tx.update("""
                    INSERT INTO backup_history (schema_version, location, result, created_by)
                    VALUES (?, ?, ?, ?)
                    """, Migrations.currentVersion(db), target.toString(), result, session.userId());
            Audit.log(tx, session, "BACKUP", "CREATE", "backup_history", fileName, null,
                    Map.of("location", target.toString(), "result", result, "size", size), null);
            return null;
        });
        if (exit != 0) {
            throw new ApiException(500, "Backup gagal. Periksa file error: " + errorFile);
        }
        return Map.of("file", target.toString(), "size", size, "result", result);
    }

    public Map<String, Object> restore(SessionInfo session, Map<String, Object> body) {
        session.require(Permissions.BACKUP_MANAGE);
        if (!session.isOwner()) {
            throw ApiException.forbidden("Restore hanya dapat dilakukan owner.");
        }
        String file = PartyService.str(body, "file");
        if (file == null || file.isBlank()) {
            throw ApiException.badRequest("File backup wajib dipilih.");
        }
        Path base = Path.of(config.backupDir()).toAbsolutePath().normalize();
        Path source = Path.of(file).toAbsolutePath().normalize();
        if (!source.startsWith(base) || !Files.isRegularFile(source)) {
            throw ApiException.badRequest("File backup harus berada di folder backup dan valid.");
        }
        if (!"RESTORE".equals(PartyService.str(body, "confirm"))) {
            throw ApiException.badRequest("Konfirmasi restore wajib dikirim dengan nilai RESTORE.");
        }
        List<String> command = new ArrayList<>();
        command.add(config.mysqlClient());
        command.add("-h");
        command.add(host());
        command.add("-P");
        command.add(String.valueOf(port()));
        command.add("-u");
        command.add(db.user());
        command.add(databaseName());
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.environment().put("MYSQL_PWD", db.password() == null ? "" : db.password());
        pb.redirectInput(source.toFile());
        File errorFile = source.resolveSibling(source.getFileName() + ".restore.err").toFile();
        pb.redirectError(errorFile);
        int exit = run(pb);
        if (exit != 0) {
            throw new ApiException(500, "Restore gagal. Periksa file error: " + errorFile);
        }
        db.withTx(tx -> {
            Audit.log(tx, session, "BACKUP", "RESTORE", "backup_history", source.getFileName().toString(),
                    null, Map.of("file", source.toString()), "Restore dari file backup");
            return null;
        });
        Migrations.run(db);
        return Map.of("restored", true, "file", source.toString());
    }

    private int run(ProcessBuilder pb) {
        try {
            Process process = pb.start();
            boolean done = process.waitFor(10, TimeUnit.MINUTES);
            if (!done) {
                process.destroyForcibly();
                throw new ApiException(500, "Proses backup/restore melebihi batas waktu.");
            }
            return process.exitValue();
        } catch (IOException e) {
            throw new ApiException(500, "Tidak dapat menjalankan alat MySQL: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(500, "Proses backup/restore terhenti.");
        }
    }

    private String databaseName() {
        String url = db.jdbcUrl();
        int question = url.indexOf('?');
        String core = question < 0 ? url : url.substring(0, question);
        return core.substring(core.lastIndexOf('/') + 1);
    }

    private String host() {
        String url = db.jdbcUrl();
        int question = url.indexOf('?');
        String core = question < 0 ? url : url.substring(0, question);
        int start = core.indexOf("//") + 2;
        int colon = core.indexOf(':', start);
        int slash = core.indexOf('/', start);
        if (colon < 0 || colon > slash) {
            return slash < 0 ? core.substring(start) : core.substring(start, slash);
        }
        return core.substring(start, colon);
    }

    private int port() {
        String url = db.jdbcUrl();
        int question = url.indexOf('?');
        String core = question < 0 ? url : url.substring(0, question);
        int start = core.indexOf("//") + 2;
        int colon = core.indexOf(':', start);
        int slash = core.indexOf('/', start);
        if (colon < 0 || colon > slash) {
            return 3306;
        }
        return Integer.parseInt(core.substring(colon + 1, slash < 0 ? core.length() : slash));
    }
}
