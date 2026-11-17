package id.peternakan.service.security;

import id.peternakan.domain.Permissions;

import java.util.Set;

public record SessionInfo(long sessionId, long userId, String username, String fullName, String roleCode,
                          Set<String> permissions, boolean mustChangePassword, String tokenHash) {

    public boolean has(String permission) {
        return permissions.contains(permission);
    }

    public boolean isOwner() {
        return "OWNER".equals(roleCode);
    }

    public boolean canViewFinance() {
        return has(Permissions.FINANCE_VIEW);
    }

    public void require(String permission) {
        if (!has(permission)) {
            throw id.peternakan.service.api.ApiException.forbidden(
                    "Anda tidak memiliki izin untuk tindakan ini.");
        }
    }

    public void requireFinance() {
        if (!canViewFinance()) {
            throw id.peternakan.service.api.ApiException.forbidden(
                    "Laporan dan data keuangan hanya dapat diakses owner.");
        }
    }
}
