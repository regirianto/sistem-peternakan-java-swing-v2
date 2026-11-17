package id.peternakan.domain;

import java.util.List;

/**
 * Status dan aturan transisi yang dipakai UI dan layanan.
 */
public final class Statuses {
    public static final List<String> GOAT_INVENTORY = List.of("AKTIF", "TERJUAL", "MATI", "DIKEMBALIKAN_KE_SUPPLIER");
    public static final List<String> GOAT_HEALTH = List.of("SEHAT", "SAKIT", "PEMULIHAN");
    public static final List<String> GOAT_READINESS = List.of("KARANTINA", "SIAP_JUAL");
    public static final List<String> PURCHASE = List.of("DRAFT", "DIKONFIRMASI", "DITERIMA_SEBAGIAN", "SELESAI", "DIBATALKAN");
    public static final List<String> SALE = List.of("DRAFT", "DIKONFIRMASI", "SELESAI", "DIBATALKAN");
    public static final List<String> PAYMENT = List.of("BELUM_BAYAR", "SEBAGIAN", "LUNAS");
    public static final List<String> DOCUMENT = List.of("DRAFT", "POSTED", "REVERSED");
    public static final List<String> ALLOCATION = List.of("DRAFT", "POSTED", "REVERSED");
    public static final List<String> SNAPSHOT = List.of("SEMENTARA", "FINAL");

    private Statuses() {
    }
}
