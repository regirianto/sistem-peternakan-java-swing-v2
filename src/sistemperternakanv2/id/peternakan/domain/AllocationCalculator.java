package id.peternakan.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Alokasi biaya bersama berdasarkan bobot hari-keberadaan.
 * Pembulatan final ke rupiah HALF_UP; sisa pembulatan dibagikan berurutan
 * ke kode kambing terkecil agar total alokasi persis sama dengan sumber.
 */
public final class AllocationCalculator {

    public record WeightedGoat(long goatId, String goatCode, long days) {
    }

    public record Allocation(long goatId, String goatCode, long days, BigDecimal amount) {
    }

    private AllocationCalculator() {
    }

    public static List<Allocation> allocate(BigDecimal total, List<WeightedGoat> goats) {
        if (total == null) {
            throw new IllegalArgumentException("Nilai sumber alokasi wajib diisi");
        }
        BigDecimal source = Money.nz(total);
        if (source.signum() < 0) {
            throw new IllegalArgumentException("Nilai sumber alokasi tidak boleh negatif");
        }
        List<WeightedGoat> valid = goats == null ? List.of()
                : goats.stream().filter(g -> g.days() > 0).toList();
        long totalDays = valid.stream().mapToLong(WeightedGoat::days).sum();
        if (totalDays <= 0) {
            throw new IllegalArgumentException("Penyebut alokasi nol: tidak ada hari-keberadaan. Sumber tetap tercatat satu kali.");
        }

        List<Allocation> result = new ArrayList<>();
        BigDecimal allocated = Money.ZERO;
        for (WeightedGoat g : valid) {
            BigDecimal raw = source.multiply(BigDecimal.valueOf(g.days()))
                    .divide(BigDecimal.valueOf(totalDays), Money.SCALE, RoundingMode.HALF_UP);
            result.add(new Allocation(g.goatId(), g.goatCode(), g.days(), raw));
            allocated = allocated.add(raw);
        }

        BigDecimal remainder = source.subtract(allocated);
        // Sisa (bisa negatif karena pembulatan) dibagikan berurutan ke kode kambing terkecil.
        List<Allocation> ordered = new ArrayList<>(result);
        ordered.sort(Comparator.comparing(Allocation::goatCode));
        int idx = 0;
        BigDecimal step = BigDecimal.valueOf(0.01);
        while (remainder.signum() != 0 && !ordered.isEmpty()) {
            Allocation target = ordered.get(idx % ordered.size());
            int direction = remainder.signum();
            BigDecimal adjust = step.multiply(BigDecimal.valueOf(direction));
            if (remainder.abs().compareTo(step) < 0) {
                adjust = remainder;
            }
            int i = result.indexOf(target);
            result.set(i, new Allocation(target.goatId(), target.goatCode(), target.days(),
                    target.amount().add(adjust)));
            remainder = remainder.subtract(adjust);
            idx++;
        }
        return result;
    }

    /**
     * Rata pembagian untuk pengiriman pembelian: dibagi jumlah kambing,
     * sisa pembulatan ke kode terkecil.
     */
    public static List<Allocation> allocateEvenly(BigDecimal total, List<WeightedGoat> goats) {
        List<WeightedGoat> equal = goats == null ? List.of()
                : goats.stream().map(g -> new WeightedGoat(g.goatId(), g.goatCode(), 1)).toList();
        return allocate(total, equal);
    }

    public static BigDecimal weightedAverage(BigDecimal oldQty, BigDecimal oldCost, BigDecimal inQty, BigDecimal inCost) {
        BigDecimal qty = Money.nz(oldQty).add(Money.nz(inQty));
        if (qty.signum() == 0) {
            return Money.ZERO;
        }
        BigDecimal value = Money.nz(oldQty).multiply(Money.nz(oldCost))
                .add(Money.nz(inQty).multiply(Money.nz(inCost)));
        return value.divide(qty, 4, RoundingMode.HALF_UP);
    }
}
