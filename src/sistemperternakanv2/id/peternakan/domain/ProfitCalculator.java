package id.peternakan.domain;

import java.math.BigDecimal;

/**
 * Laba manajerial per kambing:
 * penjualan bersih - harga pokok perolehan - beban langsung - beban bersama teralokasi.
 */
public final class ProfitCalculator {

    public record Components(BigDecimal revenue, BigDecimal hpp, BigDecimal directCost, BigDecimal allocatedCost) {
    }

    public record Result(BigDecimal revenue, BigDecimal hpp, BigDecimal directCost, BigDecimal allocatedCost,
                         BigDecimal grossMargin, BigDecimal profit, BigDecimal marginPercent) {
    }

    private ProfitCalculator() {
    }

    public static Result calculate(Components c) {
        BigDecimal revenue = Money.nz(c.revenue());
        BigDecimal hpp = Money.nz(c.hpp());
        BigDecimal direct = Money.nz(c.directCost());
        BigDecimal allocated = Money.nz(c.allocatedCost());
        BigDecimal gross = Money.nz(revenue.subtract(hpp));
        BigDecimal profit = Money.nz(gross.subtract(direct).subtract(allocated));
        return new Result(revenue, hpp, direct, allocated, gross, profit, Money.percentage(profit, revenue));
    }
}
