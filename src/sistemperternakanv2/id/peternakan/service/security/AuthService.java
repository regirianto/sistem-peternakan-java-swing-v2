package id.peternakan.service.security;

import at.favre.lib.crypto.bcrypt.BCrypt;
import id.peternakan.domain.Permissions;
import id.peternakan.service.api.ApiException;
import id.peternakan.service.db.Db;
import id.peternakan.service.support.Settings;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class AuthService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final Db db;

    public AuthService(Db db) {
        this.db = db;
    }

    public Map<String, Object> login(String username, String password, String correlationId) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw ApiException.badRequest("Username dan password wajib diisi.");
        }
        Map<String, Object> user = db.queryOne("""
                SELECT u.*, r.code AS role_code, r.name AS role_name
                FROM users u JOIN roles r ON r.id = u.role_id
                WHERE u.username = ?
                """, username.trim());
        if (user == null || !Boolean.TRUE.equals(asBool(user.get("active")))) {
            Audit.logAnonymous(db, "AUTH", "LOGIN_GAGAL", "users", username,
                    "Username atau password salah", correlationId);
            throw ApiException.unauthorized("Username atau password salah.");
        }
        String hash = Db.str(user, "password_hash");
        BCrypt.Result result = BCrypt.verifyer().verify(password.toCharArray(), hash);
        if (!result.verified) {
            Audit.logAnonymous(db, "AUTH", "LOGIN_GAGAL", "users", username,
                    "Username atau password salah", correlationId);
            throw ApiException.unauthorized("Username atau password salah.");
        }

        long userId = Db.lng(user, "id");
        Set<String> perms = permissionsForUser(userId);
        String token = newToken();
        int maxHours = Settings.getInt(db, "session.max_hours", 12);
        LocalDateTime now = LocalDateTime.now();
        db.withTx(tx -> {
            tx.update("""
                    INSERT INTO sessions (user_id, token_hash, created_at, last_seen_at, expires_at, session_version)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, userId, sha256(token), now, now, now.plusHours(maxHours),
                    Db.lng(user, "session_version"));
            return null;
        });
        Audit.logUser(db, userId, Db.str(user, "username"), "AUTH", "LOGIN", "users", String.valueOf(userId),
                null, Map.of("role", Db.str(user, "role_code")), null, correlationId);

        Map<String, Object> out = new HashMap<>();
        out.put("token", token);
        out.put("user", publicUser(user, perms));
        out.put("idleMinutes", Settings.getInt(db, "session.idle_minutes", 15));
        out.put("serverTime", now.toString());
        return out;
    }

    public SessionInfo authenticate(String token) {
        if (token == null || token.isBlank()) {
            throw ApiException.unauthorized("Sesi tidak ditemukan. Silakan masuk.");
        }
        String hash = sha256(token);
        Map<String, Object> row = db.queryOne("""
                SELECT s.id AS session_id, s.expires_at, s.last_seen_at, s.session_version AS session_version,
                       u.id AS user_id, u.username, u.full_name, u.active, u.must_change_password,
                       u.session_version AS user_session_version, r.code AS role_code
                FROM sessions s
                JOIN users u ON u.id = s.user_id
                JOIN roles r ON r.id = u.role_id
                WHERE s.token_hash = ? AND s.revoked_at IS NULL
                """, hash);
        if (row == null) {
            throw ApiException.unauthorized("Sesi tidak valid. Silakan masuk kembali.");
        }
        if (!Boolean.TRUE.equals(asBool(row.get("active")))) {
            throw ApiException.unauthorized("Akun tidak aktif.");
        }
        if (Db.lng(row, "session_version") != Db.lng(row, "user_session_version")) {
            revokeSession(Db.lng(row, "session_id"));
            throw ApiException.unauthorized("Hak akses berubah. Silakan masuk kembali.");
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expires = Db.dateTime(row, "expires_at");
        LocalDateTime lastSeen = Db.dateTime(row, "last_seen_at");
        int idle = Settings.getInt(db, "session.idle_minutes", 15);
        if (now.isAfter(expires) || now.isAfter(lastSeen.plusMinutes(idle))) {
            revokeSession(Db.lng(row, "session_id"));
            throw ApiException.unauthorized("Sesi berakhir karena tidak aktif. Silakan masuk kembali.");
        }
        db.withTx(tx -> {
            tx.update("UPDATE sessions SET last_seen_at = ? WHERE id = ?", now, Db.lng(row, "session_id"));
            return null;
        });
        long userId = Db.lng(row, "user_id");
        return new SessionInfo(Db.lng(row, "session_id"), userId, Db.str(row, "username"),
                Db.str(row, "full_name"), Db.str(row, "role_code"), permissionsForUser(userId),
                Boolean.TRUE.equals(asBool(row.get("must_change_password"))), hash);
    }

    public void logout(SessionInfo session, String correlationId) {
        db.withTx(tx -> {
            tx.update("UPDATE sessions SET revoked_at = NOW() WHERE id = ?", session.sessionId());
            return null;
        });
        Audit.logUser(db, session.userId(), session.username(), "AUTH", "LOGOUT", "sessions",
                String.valueOf(session.sessionId()), null, null, null, correlationId);
    }

    public void changePassword(SessionInfo session, String currentPassword, String newPassword) {
        if (newPassword == null || newPassword.length() < 8) {
            throw ApiException.badRequest("Password baru minimal 8 karakter.");
        }
        Map<String, Object> user = db.queryOne("SELECT password_hash FROM users WHERE id = ?", session.userId());
        if (user == null) {
            throw ApiException.notFound("Pengguna tidak ditemukan.");
        }
        if (!BCrypt.verifyer().verify(currentPassword == null ? new char[0] : currentPassword.toCharArray(),
                Db.str(user, "password_hash")).verified) {
            throw ApiException.badRequest("Password saat ini tidak sesuai.");
        }
        String hash = BCrypt.withDefaults().hashToString(12, newPassword.toCharArray());
        db.withTx(tx -> {
            tx.update("UPDATE users SET password_hash = ?, must_change_password = 0, session_version = session_version + 1 WHERE id = ?",
                    hash, session.userId());
            tx.update("UPDATE sessions SET revoked_at = NOW() WHERE user_id = ? AND id <> ?",
                    session.userId(), session.sessionId());
            tx.update("UPDATE sessions SET session_version = session_version + 1 WHERE id = ?", session.sessionId());
            return null;
        });
    }

    public Set<String> permissionsForUser(long userId) {
        return new HashSet<>(db.query("""
                SELECT p.code FROM permissions p
                JOIN role_permissions rp ON rp.permission_id = p.id
                JOIN users u ON u.role_id = rp.role_id
                WHERE u.id = ?
                """, userId).stream().map(r -> Db.str(r, "code")).toList());
    }

    /**
     * Sesi sistem tanpa token untuk perintah CLI (demo/migrasi). Tidak dapat
     * dipakai lewat HTTP karena tidak pernah diterbitkan sebagai token.
     */
    public SessionInfo systemSession(String username) {
        Map<String, Object> user = db.queryOne("""
                SELECT u.*, r.code AS role_code FROM users u JOIN roles r ON r.id = u.role_id
                WHERE u.username = ?
                """, username);
        if (user == null) {
            throw ApiException.notFound("Pengguna tidak ditemukan: " + username);
        }
        return new SessionInfo(0, Db.lng(user, "id"), Db.str(user, "username"), Db.str(user, "full_name"),
                Db.str(user, "role_code"), permissionsForUser(Db.lng(user, "id")),
                Db.lng(user, "must_change_password") == 1, "");
    }

    public List<Map<String, Object>> listUsers() {
        return db.query("""
                SELECT u.id, u.username, u.full_name, u.active, u.must_change_password, u.created_at,
                       r.code AS role_code, r.name AS role_name,
                       (SELECT GROUP_CONCAT(ub.branch_id ORDER BY ub.branch_id)
                          FROM user_branches ub WHERE ub.user_id = u.id) AS branch_ids,
                       (SELECT GROUP_CONCAT(b.name ORDER BY b.id SEPARATOR ', ')
                          FROM user_branches ub JOIN branches b ON b.id = ub.branch_id
                          WHERE ub.user_id = u.id) AS branch_names
                FROM users u JOIN roles r ON r.id = u.role_id ORDER BY u.username
                """);
    }

    public List<Map<String, Object>> listRoles() {
        return db.query("SELECT id, code, name FROM roles ORDER BY id");
    }

    public Map<String, Object> bootstrapStatus() {
        long owners = db.count("""
                SELECT COUNT(*) FROM users u JOIN roles r ON r.id = u.role_id
                WHERE r.code = 'OWNER' AND u.active = 1
                """);
        return Map.of("needsBootstrap", owners == 0, "ownerCount", owners);
    }

    public Map<String, Object> bootstrapOwner(String username, String password, String fullName) {
        if (!Boolean.TRUE.equals(bootstrapStatus().get("needsBootstrap"))) {
            throw ApiException.conflict("Bootstrap owner sudah pernah dilakukan.");
        }
        if (username == null || username.isBlank() || password == null || password.length() < 10) {
            throw ApiException.badRequest("Username wajib dan password owner minimal 10 karakter.");
        }
        if (fullName == null || fullName.isBlank()) {
            throw ApiException.badRequest("Nama lengkap wajib diisi.");
        }
        long roleId = roleId("OWNER");
        String hash = BCrypt.withDefaults().hashToString(12, password.toCharArray());
        long id = db.withTx(tx -> {
            long newId = tx.insert("""
                    INSERT INTO users (username, password_hash, full_name, role_id, active, must_change_password)
                    VALUES (?, ?, ?, ?, 1, 0)
                    """, username.trim(), hash, fullName.trim(), roleId);
            Audit.log(tx, 0, "INSTALASI", "INSTALASI", "BOOTSTRAP", "users", String.valueOf(newId), null,
                    Map.of("username", username, "role", "OWNER"), null, null);
            return newId;
        });
        return Map.of("id", id, "username", username, "role", "OWNER");
    }

    public Map<String, Object> permissionCatalog(SessionInfo session) {
        session.require(Permissions.USER_MANAGE);
        List<Map<String, Object>> permissions = db.query("SELECT id, code, description FROM permissions ORDER BY code");
        List<Map<String, Object>> roles = db.query("SELECT id, code, name FROM roles ORDER BY id");
        for (Map<String, Object> role : roles) {
            role.put("permissions", db.query("""
                    SELECT p.code FROM role_permissions rp JOIN permissions p ON p.id = rp.permission_id
                    WHERE rp.role_id = ? ORDER BY p.code
                    """, Db.lng(role, "id")).stream().map(r -> Db.str(r, "code")).toList());
        }
        return Map.of("permissions", permissions, "roles", roles,
                "ownerOnly", Permissions.OWNER_ONLY);
    }

    public Map<String, Object> updateRolePermissions(SessionInfo session, String roleCode, List<String> permissions) {
        session.require(Permissions.USER_MANAGE);
        if (!session.isOwner()) {
            throw ApiException.forbidden("Pengaturan permission hanya oleh owner.");
        }
        if ("OWNER".equals(roleCode)) {
            throw ApiException.conflict("Permission role OWNER tidak dapat dikurangi.");
        }
        if (permissions == null) {
            throw ApiException.badRequest("Daftar permission wajib dikirim.");
        }
        for (String permission : permissions) {
            if (Permissions.OWNER_ONLY.contains(permission)) {
                throw ApiException.forbidden("Permission " + permission + " hanya untuk OWNER.");
            }
        }
        long roleId = roleId(roleCode);
        db.withTx(tx -> {
            tx.update("DELETE FROM role_permissions WHERE role_id = ?", roleId);
            for (String permission : permissions) {
                Map<String, Object> row = tx.queryOne("SELECT id FROM permissions WHERE code = ?", permission);
                if (row == null) {
                    throw ApiException.badRequest("Permission tidak dikenal: " + permission);
                }
                tx.update("INSERT INTO role_permissions (role_id, permission_id) VALUES (?, ?)",
                        roleId, Db.lng(row, "id"));
            }
            tx.update("UPDATE users SET session_version = session_version + 1 WHERE role_id = ?", roleId);
            tx.update("""
                    UPDATE sessions s JOIN users u ON u.id = s.user_id
                    SET s.revoked_at = NOW() WHERE u.role_id = ?
                    """, roleId);
            Audit.log(tx, session, "USERS", "UPDATE_ROLE_PERMISSIONS", "roles", roleCode, null,
                    Map.of("permissions", permissions), "Perubahan permission role");
            return null;
        });
        return Map.of("role", roleCode, "permissions", permissions);
    }

    public Map<String, Object> createUser(SessionInfo actor, String username, String password, String fullName,
                                          String roleCode) {
        if (username == null || username.isBlank() || password == null || password.length() < 8) {
            throw ApiException.badRequest("Username wajib dan password minimal 8 karakter.");
        }
        if (fullName == null || fullName.isBlank()) {
            throw ApiException.badRequest("Nama lengkap wajib diisi.");
        }
        long roleId = roleId(roleCode);
        String hash = BCrypt.withDefaults().hashToString(12, password.toCharArray());
        return db.withTx(tx -> {
            long id = tx.insert("""
                    INSERT INTO users (username, password_hash, full_name, role_id, active, must_change_password)
                    VALUES (?, ?, ?, ?, 1, 1)
                    """, username.trim(), hash, fullName.trim(), roleId);
            if (!"OWNER".equals(roleCode)) {
                Map<String, Object> branch = tx.queryOne("""
                        SELECT id FROM branches WHERE active = 1 ORDER BY id LIMIT 1
                        """);
                if (branch != null) {
                    tx.update("INSERT INTO user_branches (user_id, branch_id) VALUES (?, ?)",
                            id, Db.lng(branch, "id"));
                }
            }
            Audit.log(tx, actor, "USERS", "CREATE", "users", String.valueOf(id), null,
                    Map.of("username", username, "role", roleCode), null);
            return Map.<String, Object>of("id", id, "username", username);
        });
    }

    public Map<String, Object> updateUser(SessionInfo actor, long userId, String fullName, String roleCode,
                                          Boolean active, String newPassword) {
        Map<String, Object> before = db.queryOne("""
                SELECT u.id, u.username, u.full_name, u.active, r.code AS role_code
                FROM users u JOIN roles r ON r.id = u.role_id WHERE u.id = ?
                """, userId);
        if (before == null) {
            throw ApiException.notFound("Pengguna tidak ditemukan.");
        }
        long ownerRole = roleId("OWNER");
        long ownerCount = db.count("SELECT COUNT(*) FROM users WHERE role_id = ? AND active = 1", ownerRole);
        boolean wasOwner = "OWNER".equals(Db.str(before, "role_code"));
        boolean willOwner = "OWNER".equals(roleCode);
        boolean willActive = active == null || active;
        if (wasOwner && (!willOwner || !willActive) && ownerCount <= 1) {
            throw ApiException.conflict("Owner aktif terakhir tidak dapat dinonaktifkan atau diturunkan rolenya.");
        }
        long newRole = roleId(roleCode == null ? Db.str(before, "role_code") : roleCode);
        String hash = newPassword == null || newPassword.isBlank() ? null
                : BCrypt.withDefaults().hashToString(12, newPassword.toCharArray());
        db.withTx(tx -> {
            tx.update("""
                    UPDATE users SET full_name = ?, role_id = ?, active = ?, session_version = session_version + 1,
                    must_change_password = CASE WHEN ? THEN 1 ELSE must_change_password END
                    WHERE id = ?
                    """, fullName == null ? Db.str(before, "full_name") : fullName, newRole,
                    willActive ? 1 : 0, hash != null ? 1 : 0, userId);
            if (hash != null) {
                tx.update("UPDATE users SET password_hash = ? WHERE id = ?", hash, userId);
            }
            tx.update("UPDATE sessions SET revoked_at = NOW() WHERE user_id = ?", userId);
            Map<String, Object> after = new HashMap<>();
            after.put("full_name", fullName);
            after.put("role", roleCode);
            after.put("active", willActive);
            after.put("password_reset", hash != null);
            Audit.log(tx, actor, "USERS", "UPDATE", "users", String.valueOf(userId), before, after, null);
            return null;
        });
        return Map.of("id", userId, "updated", true);
    }

    private long roleId(String code) {
        Map<String, Object> role = db.queryOne("SELECT id FROM roles WHERE code = ?", code);
        if (role == null) {
            throw ApiException.badRequest("Role tidak dikenal: " + code);
        }
        return Db.lng(role, "id");
    }

    private void revokeSession(long sessionId) {
        db.withTx(tx -> {
            tx.update("UPDATE sessions SET revoked_at = NOW() WHERE id = ?", sessionId);
            return null;
        });
    }

    private Map<String, Object> publicUser(Map<String, Object> user, Set<String> perms) {
        Map<String, Object> out = new HashMap<>();
        out.put("id", Db.lng(user, "id"));
        out.put("username", Db.str(user, "username"));
        out.put("fullName", Db.str(user, "full_name"));
        out.put("role", Db.str(user, "role_code"));
        out.put("roleName", Db.str(user, "role_name"));
        out.put("mustChangePassword", Boolean.TRUE.equals(asBool(user.get("must_change_password"))));
        out.put("permissions", perms.stream().sorted().toList());
        return out;
    }

    public static String sha256(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static Boolean asBool(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof Number n) {
            return n.intValue() != 0;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }
}
