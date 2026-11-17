package id.peternakan.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MoneyAndAllocationTest {

    @Test
    void profitK001MatchesPrd() {
        ProfitCalculator.Result r = ProfitCalculator.calculate(new ProfitCalculator.Components(
                Money.of("3500000"), Money.of("2500000"), Money.of("150000"), Money.of("250000")));
        assertEquals(Money.of("600000"), r.profit());
        assertEquals(new BigDecimal("17.14"), r.marginPercent());
        assertEquals(Money.of("1000000"), r.grossMargin());
    }

    @Test
    void salePerKgWithDiscount() {
        BigDecimal gross = Money.multiply(Money.of("35.5"), Money.of("100000"));
        assertEquals(Money.of("3550000"), gross);
        assertEquals(Money.of("3500000"), Money.nz(gross.subtract(Money.of("50000"))));
    }

    @Test
    void allocationUsesPresenceDays() {
        List<AllocationCalculator.Allocation> a = AllocationCalculator.allocate(Money.of("300000"), List.of(
                new AllocationCalculator.WeightedGoat(1, "K-001", 10),
                new AllocationCalculator.WeightedGoat(2, "K-002", 20)));
        assertEquals(Money.of("100000"), a.get(0).amount());
        assertEquals(Money.of("200000"), a.get(1).amount());
    }

    @Test
    void allocationRemainderGoesToSmallestCodeAndTotalsExactly() {
        List<AllocationCalculator.Allocation> a = AllocationCalculator.allocate(Money.of("100"), List.of(
                new AllocationCalculator.WeightedGoat(3, "K-003", 1),
                new AllocationCalculator.WeightedGoat(1, "K-001", 1),
                new AllocationCalculator.WeightedGoat(2, "K-002", 1)));
        BigDecimal total = a.stream().map(AllocationCalculator.Allocation::amount)
                .reduce(Money.ZERO, BigDecimal::add);
        assertEquals(Money.of("100"), total);
        assertEquals(Money.of("33.34"), a.stream().filter(x -> x.goatCode().equals("K-001")).findFirst().orElseThrow().amount());
    }

    @Test
    void allocationRejectsZeroDenominator() {
        assertThrows(IllegalArgumentException.class, () -> AllocationCalculator.allocate(Money.of("100"), List.of(
                new AllocationCalculator.WeightedGoat(1, "K-001", 0))));
    }

    @Test
    void marginPercentNullWhenZeroRevenue() {
        assertNull(Money.percentage(Money.of("10"), Money.ZERO));
    }

    @Test
    void weightedAverageFeed() {
        // 10 kg @ 20.000 = 200.000; masuk 10 kg @ 30.000; rata-rata 25.000
        assertEquals(new BigDecimal("25000.0000"),
                AllocationCalculator.weightedAverage(Money.of("10"), Money.of("20000"), Money.of("10"), Money.of("30000")));
    }

    @Test
    void feedStockExampleAc11() {
        // masuk 500.000 lalu terpakai 200.000 -> sisa nilai 300.000
        BigDecimal stock = Money.of("500000").subtract(Money.of("200000"));
        assertEquals(Money.of("300000"), stock);
    }
}
