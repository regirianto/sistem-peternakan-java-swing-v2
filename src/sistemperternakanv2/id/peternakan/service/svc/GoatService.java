package id.peternakan.service.svc;

import id.peternakan.domain.Money;
import id.peternakan.domain.Permissions;
import id.peternakan.service.api.ApiException;
import id.peternakan.service.config.AppConfig;
import id.peternakan.service.db.Db;
import id.peternakan.service.repo.AttachmentRepository;
import id.peternakan.service.repo.GoatRepository;
import id.peternakan.service.repo.PenRepository;
import id.peternakan.service.repo.ProfitRepository;
import id.peternakan.service.security.Audit;
import id.peternakan.service.security.SessionInfo;
import id.peternakan.service.support.BranchAccess;
import id.peternakan.service.support.DocNo;
import id.peternakan.service.support.Idempotency;
import id.peternakan.service.support.Journal;
import id.peternakan.service.support.PeriodGuard;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class GoatService {
    private final Db db;
    private final GoatRepository goats;
    private final PenRepository pens;
    private final ProfitRepository profits;
    private final AttachmentRepository attachments;
    private final AppConfig config;

    public GoatService(Db db, GoatRepository goats, PenRepository pens, ProfitRepository profits,
                       AttachmentRepository attachments, AppConfig config) {
        this.db = db;
        this.goats = goats;
        this.pens = pens;
        this.profits = profits;
        this.attachments = attachments;
        this.config = config;
    }

    public Map<String, Object> list(SessionInfo session, String q, String status, String health, String readiness,
                                    Long supplierId, Long penId, String sort, int page, int size,
                                    Long branchId) {
        session.require(Permissions.GOAT_READ);
        Long filter = BranchAccess.filter(db, session, branchId);
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> goat : goats.list(q, status, health, readiness, supplierId, penId, sort,
                page, size, filter)) {
            items.add(maskGoat(session, goat));
        }
        Map<String, Object> out = new HashMap<>();
        out.put("items", items);
        out.put("total", goats.count(q, status, health, readiness, supplierId, penId, filter));
        return out;
    }

    public Map<String, Object> find(SessionInfo session, long id) {
        session.require(Permissions.GOAT_READ);
        Map<String, Object> goat = goats.find(id);
        BranchAccess.requireBranch(db, session, Db.lng(goat, "branch_id"));
        Map<String, Object> history = goats.history(id);
        if (!session.isOwner()) {
            history.remove("expenses");
            history.remove("allocations");
            for (Map<String, Object> sale : cast(history.get("sales"))) {
                sale.remove("net");
                sale.remove("hpp");
            }
        }
        goat.put("history", history);
        if (session.isOwner()) {
            goat.put("profit", profits.latestSnapshot(id));
            goat.put("profitVersions", profits.snapshots(id));
            goat.put("profitLive", db.withTx(tx -> ProfitService.componentsToMap(profits.components(tx, id))));
        }
        return maskGoat(session, goat);
    }

    public Map<String, Object> addWeight(SessionInfo session, long goatId, Map<String, Object> body) {
        session.require(Permissions.WEIGHT_RECORD);
        LocalDate date = LocalDate.parse(PartyService.str(body, "measuredAt"));
        BigDecimal weight = Money.of(String.valueOf(body.get("weight")));
        db.withTx(tx -> {
            requireActive(tx, session, goatId);
            goats.insertWeight(tx, goatId, date, weight, session.userId(), PartyService.str(body, "notes"));
            Audit.log(tx, session, "GOATS", "WEIGHT", "goats", String.valueOf(goatId), null,
                    Map.of("tanggal", date.toString(), "berat", weight), null);
            return null;
        });
        return Map.of("goatId", goatId, "recorded", true);
    }

    public Map<String, Object> addHealth(SessionInfo session, long goatId, Map<String, Object> body) {
        session.require(Permissions.HEALTH_RECORD);
        LocalDate date = LocalDate.parse(PartyService.str(body, "occurredAt"));
        String status = PartyService.str(body, "healthStatus");
        db.withTx(tx -> {
            requireActive(tx, session, goatId);
            goats.insertHealth(tx, goatId, date, PartyService.str(body, "complaint"),
                    PartyService.str(body, "examination"), PartyService.str(body, "action"),
                    PartyService.str(body, "medication"),
                    Money.of(String.valueOf(body.getOrDefault("cost", "0"))),
                    PartyService.str(body, "followUpDate") == null ? null
                            : LocalDate.parse(PartyService.str(body, "followUpDate")),
                    PartyService.str(body, "responsible"), session.userId());
            if (status != null && !status.isBlank()) {
                goats.setHealthStatus(tx, goatId, status);
            }
            Audit.log(tx, session, "HEALTH", "CREATE", "goats", String.valueOf(goatId), null, body, null);
            return null;
        });
        return Map.of("goatId", goatId, "recorded", true);
    }

    public Map<String, Object> move(SessionInfo session, long goatId, Map<String, Object> body) {
        session.require(Permissions.GOAT_OPERATIONAL);
        long penId = Long.parseLong(String.valueOf(body.get("penId")));
        LocalDate date = body.get("date") == null ? LocalDate.now() : LocalDate.parse(String.valueOf(body.get("date")));
        String reason = PartyService.str(body, "reason");
        db.withTx(tx -> {
            Map<String, Object> goat = requireActive(tx, session, goatId);
            Map<String, Object> pen = tx.queryOne("SELECT * FROM pens WHERE id = ?", penId);
            if (pen == null) {
                throw ApiException.notFound("Kandang tidak ditemukan.");
            }
            if (Db.lng(pen, "branch_id") != Db.lng(goat, "branch_id")) {
                throw ApiException.conflict("Perpindahan kambing antar cabang belum didukung.");
            }
            Map<String, Object> current = pens.currentPlacement(tx, goatId);
            if (current != null && Db.lng(current, "pen_id") == penId) {
                throw ApiException.conflict("Kambing sudah berada di kandang tersebut.");
            }
            pens.ensureCapacity(tx, penId);
            LocalDateTime at = date.atTime(12, 0);
            pens.closePlacement(tx, goatId, at, reason);
            pens.insertPlacement(tx, goatId, penId, at, session.userId(), reason);
            Audit.log(tx, session, "GOATS", "MOVE", "goats", String.valueOf(goatId),
                    current == null ? null : Map.of("penId", Db.lng(current, "pen_id")),
                    Map.of("penId", penId, "tanggal", date.toString()), reason);
            return null;
        });
        return Map.of("goatId", goatId, "moved", true);
    }

    public Map<String, Object> readiness(SessionInfo session, long goatId, Map<String, Object> body) {
        session.require(Permissions.HEALTH_MANAGE);
        String readiness = PartyService.str(body, "readiness");
        db.withTx(tx -> {
            Map<String, Object> goat = tx.queryOne("SELECT * FROM goats WHERE id=? FOR UPDATE", goatId);
            if (goat == null) {
                throw ApiException.notFound("Kambing tidak ditemukan.");
            }
            BranchAccess.requireBranch(db, session, Db.lng(goat, "branch_id"));
            goats.setReadiness(tx, goatId, readiness);
            Audit.log(tx, session, "GOATS", "READINESS", "goats", String.valueOf(goatId),
                    Map.of("readiness", Db.str(goat, "sale_readiness")), Map.of("readiness", readiness), null);
            return null;
        });
        return Map.of("goatId", goatId, "readiness", readiness);
    }

    public Map<String, Object> death(SessionInfo session, long goatId, Map<String, Object> body, String requestId) {
        if (!session.isOwner()) {
            throw ApiException.forbidden("Konfirmasi kematian kambing hanya dapat dilakukan owner.");
        }
        String reason = PartyService.str(body, "reason");
        if (reason == null || reason.isBlank()) {
            throw ApiException.badRequest("Alasan kematian wajib diisi.");
        }
        LocalDate date = LocalDate.parse(String.valueOf(body.get("deathDate")));
        return db.withTx(tx -> Idempotency.run(tx, requestId, session.userId(), "GOAT_DEATH", () -> {
            Map<String, Object> goat = tx.queryOne("SELECT * FROM goats WHERE id=? FOR UPDATE", goatId);
            if (goat == null) {
                throw ApiException.notFound("Kambing tidak ditemukan.");
            }
            if (!"AKTIF".equals(Db.str(goat, "status"))) {
                throw ApiException.conflict("Kambing tidak berstatus aktif.");
            }
            BranchAccess.requireBranch(db, session, Db.lng(goat, "branch_id"));
            PeriodGuard.requireOpen(tx, date);
            tx.update("""
                    UPDATE goats SET status='MATI', death_date=?, death_reason=?, version=version+1 WHERE id=?
                    """, date, reason, goatId);
            tx.update("UPDATE pen_placements SET exited_at=? WHERE goat_id=? AND exited_at IS NULL",
                    date.atTime(23, 59, 59), goatId);
            String docNo = DocNo.next(tx, "WO", date);
            BigDecimal value = Db.dec(goat, "acquisition_cost");
            tx.insert("""
                    INSERT INTO inventory_writeoffs (doc_no, goat_id, writeoff_date, value, reason, created_by)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, docNo, goatId, date, value, reason, session.userId());
            Journal.post(tx, date, Db.lng(goat, "branch_id"), "WRITEOFF", goatId, "WRITEOFF:" + goatId,
                    "Penghapusan kambing " + Db.str(goat, "code"), session.userId(), List.of(
                            Journal.Line.debit("6104", value, "Kerugian persediaan"),
                            Journal.Line.credit("1301", value, "Persediaan kambing")));
            Audit.log(tx, session, "GOATS", "DEATH", "goats", String.valueOf(goatId), goat,
                    Map.of("status", "MATI", "docNo", docNo, "value", value), reason);
            return Map.of("docNo", docNo, "goatId", goatId, "value", value);
        }));
    }

    public Map<String, Object> setPhoto(SessionInfo session, long goatId, Map<String, Object> body) {
        session.require(Permissions.GOAT_UPDATE);
        String base64 = PartyService.str(body, "contentBase64");
        String filename = PartyService.str(body, "filename");
        String mime = PartyService.str(body, "mimeType");
        if (base64 == null || base64.isBlank()) {
            throw ApiException.badRequest("File foto wajib diunggah.");
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Format file foto tidak valid.");
        }
        if (bytes.length > 5 * 1024 * 1024) {
            throw ApiException.badRequest("Ukuran foto maksimal 5 MB.");
        }
        String key = UUID.randomUUID() + "-" + (filename == null ? "foto" : filename.replaceAll("[^a-zA-Z0-9._-]", "_"));
        try {
            Path dir = Path.of(config.attachmentDir());
            Files.createDirectories(dir);
            Files.write(dir.resolve(key), bytes);
        } catch (IOException e) {
            throw new ApiException(500, "Gagal menyimpan foto: " + e.getMessage());
        }
        String checksum = checksum(bytes);
        long attachmentId = db.withTx(tx -> {
            requireActive(tx, session, goatId);
            long id = attachments.insert(tx, filename == null ? "foto" : filename,
                    mime == null ? "image/jpeg" : mime, bytes.length, checksum, key, session.userId());
            goats.setPhoto(tx, goatId, id);
            Audit.log(tx, session, "GOATS", "PHOTO", "goats", String.valueOf(goatId), null,
                    Map.of("attachmentId", id, "size", bytes.length), null);
            return id;
        });
        return Map.of("attachmentId", attachmentId);
    }

    public Map<String, Object> photo(SessionInfo session, long goatId) {
        Map<String, Object> goat = goats.find(goatId);
        BranchAccess.requireBranch(db, session, Db.lng(goat, "branch_id"));
        Object attachmentId = goat.get("photo_attachment_id");
        if (attachmentId == null) {
            throw ApiException.notFound("Kambing belum memiliki foto.");
        }
        Map<String, Object> attachment = attachments.find(((Number) attachmentId).longValue());
        try {
            byte[] bytes = Files.readAllBytes(Path.of(config.attachmentDir()).resolve(Db.str(attachment, "storage_key")));
            return Map.of("bytes", bytes, "mimeType", Db.str(attachment, "mime_type"),
                    "filename", Db.str(attachment, "original_name"));
        } catch (IOException e) {
            throw new ApiException(500, "File foto tidak ditemukan.");
        }
    }

    public Map<String, Object> sellable(SessionInfo session, Long branchId) {
        session.require(Permissions.SALE_MANAGE);
        Long filter = BranchAccess.filter(db, session, branchId);
        return Map.of("items", goats.sellable(filter));
    }

    private Map<String, Object> requireActive(Db.Tx tx, SessionInfo session, long goatId) {
        Map<String, Object> goat = tx.queryOne("SELECT status, branch_id FROM goats WHERE id=? FOR UPDATE", goatId);
        if (goat == null) {
            throw ApiException.notFound("Kambing tidak ditemukan.");
        }
        BranchAccess.requireBranch(db, session, Db.lng(goat, "branch_id"));
        if (!"AKTIF".equals(Db.str(goat, "status"))) {
            throw ApiException.conflict("Kambing tidak aktif.");
        }
        return goat;
    }

    private Map<String, Object> maskGoat(SessionInfo session, Map<String, Object> goat) {
        Map<String, Object> copy = new HashMap<>(goat);
        if (!session.canViewFinance() && !"ADMIN".equals(session.roleCode())) {
            copy.remove("acquisition_cost");
        }
        if (!session.isOwner()) {
            copy.remove("profit");
            copy.remove("profitVersions");
            copy.remove("profitLive");
        }
        return copy;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> cast(Object value) {
        return value instanceof List<?> list ? (List<Map<String, Object>>) list : List.of();
    }

    private String checksum(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception e) {
            return "";
        }
    }
}
