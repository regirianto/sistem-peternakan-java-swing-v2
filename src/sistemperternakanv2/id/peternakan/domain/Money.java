package id.peternakan.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * Semua perhitungan uang memakai BigDecimal dengan skala 2 dan HALF_UP.
 */
public final class Money {
    public static final int SCALE = 2;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(SCALE, ROUNDING);

    private Money() {
    }

    public static BigDecimal of(String value) {
        return value == null || value.isBlank() ? ZERO : decimal(value, SCALE);
    }

    /** Angka kuantitas (berat/kg) dengan 3 desimal, mis. "12,5" menjadi 12.500. */
    public static BigDecimal quantity(String value) {
        return value == null || value.isBlank() ? BigDecimal.ZERO : decimal(value, 3);
    }

    /**
     * Angka dari input pengguna: spasi dibuang, koma desimal diterima, dan
     * pemisah ribuan gaya Indonesia ("2.500.000,50") dinormalkan.
     */
    private static BigDecimal decimal(String value, int scale) {
        String text = value.trim().replace(" ", "");
        int comma = text.lastIndexOf(',');
        if (comma >= 0) {
            text = text.substring(0, comma).replace(".", "") + "." + text.substring(comma + 1);
        }
        return new BigDecimal(text).setScale(scale, ROUNDING);
    }

    public static BigDecimal of(double value) {
        return BigDecimal.valueOf(value).setScale(SCALE, ROUNDING);
    }

    public static BigDecimal nz(BigDecimal value) {
        return value == null ? ZERO : value.setScale(SCALE, ROUNDING);
    }

    public static BigDecimal round(BigDecimal value) {
        return nz(value);
    }

    public static BigDecimal multiply(BigDecimal a, BigDecimal b) {
        return nz(nz(a).multiply(nz(b)));
    }

    public static BigDecimal percentage(BigDecimal part, BigDecimal total) {
        if (nz(total).signum() == 0) {
            return null;
        }
        return nz(part).multiply(BigDecimal.valueOf(100)).divide(nz(total), 2, ROUNDING);
    }

    public static String format(BigDecimal value) {
        NumberFormat nf = NumberFormat.getCurrencyInstance(Locale.of("id", "ID"));
        nf.setMaximumFractionDigits(0);
        nf.setMinimumFractionDigits(0);
        return nf.format(nz(value)).replace("Rp", "Rp").replace(",00", "");
    }

    public static String plain(BigDecimal value) {
        return nz(value).toPlainString();
    }
}
