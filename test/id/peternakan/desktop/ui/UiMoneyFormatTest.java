package id.peternakan.desktop.ui;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UiMoneyFormatTest {
    @Test
    void formatsMoneyWithRupiahPrefixAndIndonesianGrouping() {
        assertEquals("Rp 1.250.000", Ui.formatMoney(new BigDecimal("1250000")));
        assertEquals("Rp 0", Ui.formatMoney(0));
        assertEquals("", Ui.formatMoney(null));
    }
}
