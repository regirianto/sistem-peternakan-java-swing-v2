package id.peternakan.service.svc;

import id.peternakan.domain.Permissions;
import id.peternakan.service.db.Db;
import id.peternakan.service.security.Audit;
import id.peternakan.service.security.SessionInfo;
import id.peternakan.service.support.Settings;

import java.util.Map;

public final class SettingsService {
    private final Db db;

    public SettingsService(Db db) {
        this.db = db;
    }

    public Map<String, Object> all(SessionInfo session) {
        session.require(Permissions.SETTINGS_MANAGE);
        return Map.of("items", Settings.all(db));
    }

    public Map<String, Object> update(SessionInfo session, Map<String, Object> body) {
        session.require(Permissions.SETTINGS_MANAGE);
        if (!session.isOwner()) {
            throw id.peternakan.service.api.ApiException.forbidden("Pengaturan usaha hanya oleh owner.");
        }
        db.withTx(tx -> {
            for (Map.Entry<String, Object> entry : body.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue() == null ? "" : String.valueOf(entry.getValue());
                Settings.set(tx, key, value);
            }
            Audit.log(tx, session, "SETTINGS", "UPDATE", "app_settings", null, null, body, null);
            return null;
        });
        return Map.of("updated", true, "items", Settings.all(db));
    }

    public Map<String, Object> publicInfo() {
        return Map.of(
                "companyName", Settings.get(db, "company.name", "Peternakan Kambing"),
                "companyAddress", Settings.get(db, "company.address", ""),
                "companyPhone", Settings.get(db, "company.phone", ""));
    }
}
