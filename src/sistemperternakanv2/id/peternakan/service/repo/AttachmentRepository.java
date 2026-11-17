package id.peternakan.service.repo;

import id.peternakan.service.api.ApiException;
import id.peternakan.service.db.Db;

import java.util.Map;

public final class AttachmentRepository {
    private final Db db;

    public AttachmentRepository(Db db) {
        this.db = db;
    }

    public long insert(Db.Tx tx, String originalName, String mimeType, long size, String checksum,
                       String storageKey, long userId) {
        return tx.insert("""
                INSERT INTO attachments (original_name, mime_type, size_bytes, checksum, storage_key, created_by)
                VALUES (?, ?, ?, ?, ?, ?)
                """, originalName, mimeType, size, checksum, storageKey, userId);
    }

    public Map<String, Object> find(long id) {
        Map<String, Object> row = db.queryOne("SELECT * FROM attachments WHERE id = ?", id);
        if (row == null) {
            throw ApiException.notFound("Lampiran tidak ditemukan.");
        }
        return row;
    }
}
